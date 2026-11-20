package com.example.ticket.ticket.service.impl;

import com.example.ticket.common.api.PageResult;
import com.example.ticket.common.annotation.DataScope;
import com.example.ticket.common.context.IdempotentContext;
import com.example.ticket.common.context.LoginUser;
import com.example.ticket.common.context.UserContext;
import com.example.ticket.common.exception.BizException;
import com.example.ticket.common.exception.ErrorCode;
import com.example.ticket.common.idempotent.IdempotentRecordMapper;
import com.example.ticket.system.entity.SysUser;
import com.example.ticket.system.mapper.SysDeptMapper;
import com.example.ticket.system.mapper.SysUserMapper;
import com.example.ticket.ticket.dto.AssignReq;
import com.example.ticket.ticket.dto.CommentReq;
import com.example.ticket.ticket.dto.RemarkReq;
import com.example.ticket.ticket.dto.TicketCasParam;
import com.example.ticket.ticket.dto.TicketCreateReq;
import com.example.ticket.ticket.dto.TicketQueryReq;
import com.example.ticket.ticket.dto.TransferReq;
import com.example.ticket.ticket.dto.VerifyReq;
import com.example.ticket.ticket.entity.Ticket;
import com.example.ticket.ticket.entity.TicketComment;
import com.example.ticket.ticket.entity.TicketFlowLog;
import com.example.ticket.ticket.entity.TicketType;
import com.example.ticket.ticket.enums.TicketAction;
import com.example.ticket.ticket.enums.TicketStatus;
import com.example.ticket.ticket.mapper.TicketCommentMapper;
import com.example.ticket.ticket.mapper.TicketFlowLogMapper;
import com.example.ticket.ticket.mapper.TicketMapper;
import com.example.ticket.ticket.mapper.TicketTypeMapper;
import com.example.ticket.ticket.service.TicketNoGenerator;
import com.example.ticket.ticket.service.TicketService;
import com.example.ticket.ticket.vo.FlowLogVO;
import com.example.ticket.ticket.vo.TicketDetailVO;
import com.example.ticket.ticket.vo.TicketVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final DateTimeFormatter CURSOR_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int INSERT_RETRY = 3;

    private final TicketMapper ticketMapper;
    private final TicketFlowLogMapper flowLogMapper;
    private final TicketCommentMapper commentMapper;
    private final TicketTypeMapper typeMapper;
    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final IdempotentRecordMapper idempotentRecordMapper;
    private final TicketNoGenerator ticketNoGenerator;

    @Value("${app.ticket.reopen-days:7}")
    private int reopenDays;

    // ================================================================== 提交

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TicketDetailVO submit(TicketCreateReq req) {
        LoginUser user = UserContext.require();

        TicketType type = typeMapper.selectById(req.getTypeId());
        if (type == null) {
            throw new BizException(ErrorCode.TICKET_TYPE_NOT_FOUND);
        }
        if (type.getSlaHours() == null || type.getSlaHours() <= 0) {
            throw new BizException(ErrorCode.SLA_CONFIG_MISSING);
        }

        // 幂等第 2 层：与 Redis/内存令牌共用同一个键写兜底记录，唯一索引冲突即重复提交
        writeIdempotentRecord("TICKET_SUBMIT", req.getTitle() + "|" + req.getTypeId() + "|" + req.getPriority());

        Ticket ticket = new Ticket();
        ticket.setTitle(req.getTitle());
        ticket.setContent(req.getContent());
        ticket.setTypeId(req.getTypeId());
        ticket.setPriority(req.getPriority());
        ticket.setStatus(TicketStatus.PENDING_ASSIGN.getCode());
        ticket.setCreatorId(user.getId());
        ticket.setCreatorDept(user.getDeptId());
        // SLA 落到库而不是查询时算：规则会变，历史工单必须按当时的规则算，否则统计全错
        ticket.setDeadline(LocalDateTime.now().plusHours(type.getSlaHours()));

        insertWithRetry(ticket);

        writeFlow(ticket.getId(), TicketAction.SUBMIT,
                TicketStatus.DRAFT, TicketStatus.PENDING_ASSIGN, user, null);

        log.info("ticket submitted, no={}, id={}, creator={}",
                ticket.getTicketNo(), ticket.getId(), user.getUsername());
        return detail(ticket.getId());
    }

    /**
     * 工单号冲突重试。
     * <p>单实例下几乎不会发生；多实例（各自持有计数器）时靠 uk_ticket_no 拦下并换号重试，
     * 而不是直接把 DuplicateKeyException 抛成"重复提交"——那会把号段冲突误报成用户操作问题。</p>
     */
    private void insertWithRetry(Ticket ticket) {
        for (int i = 0; i < INSERT_RETRY; i++) {
            ticket.setTicketNo(ticketNoGenerator.next());
            try {
                ticketMapper.insert(ticket);
                return;
            } catch (DuplicateKeyException e) {
                log.warn("ticket_no conflict, retry {}/{}, no={}", i + 1, INSERT_RETRY, ticket.getTicketNo());
            }
        }
        throw new BizException(ErrorCode.REPEAT_SUBMIT, "工单号生成冲突，请重试");
    }

    private void writeIdempotentRecord(String bizType, String payload) {
        String token = IdempotentContext.get();
        if (!StringUtils.hasText(token)) {
            // 内部调用 / 单测没有经过切面，此时由业务唯一约束兜底
            return;
        }
        try {
            idempotentRecordMapper.insert(bizType, token, sha256(payload));
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.REPEAT_SUBMIT);
        }
    }

    // ================================================================== 查询

    @Override
    @DataScope(alias = "t")
    public PageResult<TicketVO> page(TicketQueryReq req) {
        LoginUser user = UserContext.require();
        if (req.getPageSize() != null && (req.getPageSize() < 1 || req.getPageSize() > MAX_PAGE_SIZE)) {
            // pageSize 上限必须校验，否则 pageSize=100000 就是一次拖库
            throw new BizException(ErrorCode.PAGE_SIZE_EXCEED);
        }
        applyTab(req, user);
        req.normalizeTimeRange();
        req.applyOffset();

        boolean cursor = req.isCursorMode();
        if (cursor) {
            req.setOffset(0);
        }

        List<TicketVO> list = ticketMapper.selectPage(req);
        fillNames(list);
        fillTimeoutFlag(list);

        if (cursor) {
            TicketVO last = list.isEmpty() ? null : list.get(list.size() - 1);
            return new PageResult<>(list, null, 0, req.getPageSize(),
                    last == null ? null : last.getCreateTime().format(CURSOR_FMT),
                    last == null ? null : last.getId());
        }

        // 非首页默认不统计总数：深分页下 count 往往比主查询还慢
        boolean needTotal = !Boolean.FALSE.equals(req.getNeedTotal());
        Long total = needTotal ? ticketMapper.countPage(req) : null;
        return new PageResult<>(list, total, req.getPageNum(), req.getPageSize(), null, null);
    }

    /** 把前端 Tab 翻译成服务端条件：归属人一律取自登录态，不接受客户端传入 */
    private void applyTab(TicketQueryReq req, LoginUser user) {
        String tab = req.getTab() == null ? "created" : req.getTab();
        switch (tab) {
            case "created" -> req.setCreatorId(user.getId());
            case "todo" -> {
                req.setAssigneeId(user.getId());
                if (req.getStatus() == null || req.getStatus().isEmpty()) {
                    // 待办 = 待处理 + 处理中；挂起的不算待办
                    req.setStatus(List.of(TicketStatus.PENDING_PROCESS.getCode(),
                            TicketStatus.PROCESSING.getCode()));
                }
            }
            default -> {
                // dept / all：范围由 @DataScope 决定，这里不加额外条件
            }
        }
    }

    /**
     * 批量补人名，避免列表里的 2N 次单查（N+1）。
     */
    private void fillNames(List<TicketVO> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (TicketVO vo : list) {
            if (vo.getCreatorId() != null) {
                ids.add(vo.getCreatorId());
            }
            if (vo.getAssigneeId() != null) {
                ids.add(vo.getAssigneeId());
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, String> nameMap = new HashMap<>();
        for (SysUser u : userMapper.selectNamesByIds(ids)) {
            nameMap.put(u.getId(), u.getRealName());
        }
        for (TicketVO vo : list) {
            vo.setCreatorName(nameMap.get(vo.getCreatorId()));
            vo.setAssigneeName(vo.getAssigneeId() == null ? null : nameMap.get(vo.getAssigneeId()));
        }
    }

    private void fillTimeoutFlag(List<TicketVO> list) {
        LocalDateTime now = LocalDateTime.now();
        for (TicketVO vo : list) {
            boolean timeout = vo.getDeadline() != null && vo.getDeadline().isBefore(now)
                    && vo.getStatus() != null
                    && vo.getStatus() != TicketStatus.CLOSED.getCode()
                    && vo.getStatus() != TicketStatus.REJECTED.getCode();
            vo.setTimeout(timeout);
        }
    }

    @Override
    public TicketDetailVO detail(Long id) {
        LoginUser user = UserContext.require();
        Ticket t = mustGetVisible(id, user);
        return buildDetail(t, user);
    }

    @Override
    public List<FlowLogVO> flows(Long id) {
        LoginUser user = UserContext.require();
        mustGetVisible(id, user);
        return flowLogMapper.selectByTicketId(id);
    }

    @Override
    public List<TicketComment> comments(Long id) {
        LoginUser user = UserContext.require();
        Ticket t = mustGetVisible(id, user);
        return commentMapper.selectByTicketId(id, canSeeInternal(t, user));
    }

    @Override
    public List<TicketType> types() {
        return typeMapper.selectAll();
    }

    private TicketDetailVO buildDetail(Ticket t, LoginUser user) {
        TicketDetailVO vo = new TicketDetailVO();
        vo.setId(t.getId());
        vo.setTicketNo(t.getTicketNo());
        vo.setTitle(t.getTitle());
        vo.setContent(t.getContent());
        vo.setTypeId(t.getTypeId());
        vo.setPriority(t.getPriority());
        vo.setStatus(t.getStatus());
        vo.setCreatorId(t.getCreatorId());
        vo.setAssigneeId(t.getAssigneeId());
        vo.setCreateTime(t.getCreateTime());
        vo.setDeadline(t.getDeadline());
        vo.setAssignTime(t.getAssignTime());
        vo.setFinishTime(t.getFinishTime());
        vo.setCloseTime(t.getCloseTime());
        vo.setTimeout(t.getDeadline() != null && t.getDeadline().isBefore(LocalDateTime.now())
                && t.getStatus() != TicketStatus.CLOSED.getCode()
                && t.getStatus() != TicketStatus.REJECTED.getCode());

        List<TicketVO> one = new ArrayList<>(1);
        one.add(vo);
        fillNames(one);
        vo.setAssigneeDeptName(t.getAssigneeDept() == null ? null : deptMapper.selectNameById(t.getAssigneeDept()));
        vo.setCreatorDeptName(t.getCreatorDept() == null ? null : deptMapper.selectNameById(t.getCreatorDept()));

        // 固定 2 次查询：主表 + 日志；日志里的 operatorName 已冗余，不需要回查用户表
        vo.setFlows(flowLogMapper.selectByTicketId(t.getId()));
        vo.setComments(commentMapper.selectByTicketId(t.getId(), canSeeInternal(t, user)));
        vo.setAvailableActions(resolveActions(t, user));
        return vo;
    }

    // ================================================================== 动作

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assign(Long id, AssignReq req) {
        LoginUser user = UserContext.require();
        Ticket t = mustGetVisible(id, user);
        // 调度只能派本部门的单（数据权限的"写"侧，读侧由 SQL 片段负责）
        if (user.getDataScope() != null && user.getDataScope() == 2
                && !Objects.equals(t.getCreatorDept(), user.getDeptId())) {
            throw new BizException(ErrorCode.DATA_SCOPE_DENIED, "只能分派本部门的工单");
        }
        transit(id, TicketAction.ASSIGN, user, req.getRemark(), req.getAssigneeId(), false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void accept(Long id) {
        LoginUser user = UserContext.require();
        mustGetVisible(id, user);
        transit(id, TicketAction.ACCEPT, user, "接单", null, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finish(Long id, RemarkReq req) {
        LoginUser user = UserContext.require();
        mustGetVisible(id, user);
        transit(id, TicketAction.FINISH, user, req == null ? null : req.getRemark(), null, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void verify(Long id, VerifyReq req) {
        LoginUser user = UserContext.require();
        mustGetVisible(id, user);
        if (Boolean.TRUE.equals(req.getPass())) {
            transit(id, TicketAction.VERIFY_PASS, user, req.getRemark(), null, false);
        } else {
            if (!StringUtils.hasText(req.getRemark())) {
                throw new BizException(ErrorCode.PARAM_MISSING, "验收驳回必须填写原因");
            }
            transit(id, TicketAction.VERIFY_REJECT, user, req.getRemark(), null, false);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transfer(Long id, TransferReq req) {
        LoginUser user = UserContext.require();
        mustGetVisible(id, user);
        transit(id, TicketAction.TRANSFER, user, req.getRemark(), req.getAssigneeId(), false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void suspend(Long id, RemarkReq req) {
        LoginUser user = UserContext.require();
        mustGetVisible(id, user);
        transit(id, TicketAction.SUSPEND, user, req == null ? null : req.getRemark(), null, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resume(Long id, RemarkReq req) {
        LoginUser user = UserContext.require();
        mustGetVisible(id, user);
        transit(id, TicketAction.RESUME, user, req == null ? null : req.getRemark(), null, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void close(Long id, RemarkReq req) {
        LoginUser user = UserContext.require();
        // 强制关闭允许从任意非终态发起：调度/管理员关掉"卡住不再需要处理"的单，
        // 是运维现实需求，若强行要求先走完验收流程，线上只会出现大量僵尸工单。
        Ticket t = mustGetVisible(id, user);
        if (t.getStatus() == TicketStatus.CLOSED.getCode()
                || t.getStatus() == TicketStatus.REJECTED.getCode()) {
            throw new BizException(ErrorCode.ILLEGAL_STATUS_TRANSITION, "工单已结束，无需关闭");
        }
        transit(id, TicketAction.CLOSE, user, req == null ? null : req.getRemark(), null, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reopen(Long id, RemarkReq req) {
        LoginUser user = UserContext.require();
        Ticket t = mustGetVisible(id, user);
        if (t.getCloseTime() != null && t.getCloseTime().isBefore(LocalDateTime.now().minusDays(reopenDays))) {
            throw new BizException(ErrorCode.REOPEN_EXPIRED);
        }
        transit(id, TicketAction.REOPEN, user, req == null ? null : req.getRemark(), null, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long comment(Long id, CommentReq req) {
        LoginUser user = UserContext.require();
        Ticket t = mustGetVisible(id, user);
        boolean internal = Boolean.TRUE.equals(req.getInternal()) && canSeeInternal(t, user);
        TicketComment c = new TicketComment();
        c.setTicketId(id);
        c.setUserId(user.getId());
        c.setUserName(user.getRealName());
        c.setContent(req.getContent());
        c.setIsInternal(internal ? 1 : 0);
        commentMapper.insert(c);
        return c.getId();
    }

    // ============================================================== 状态流转

    /**
     * 统一的状态变更入口 —— 全项目只有这里能改 ticket 的状态和写 flow_log。
     *
     * <p>为什么是 private：Spring 的事务与切面都基于代理，同类内部调用不会经过代理，
     * 把 @Transactional 标在 private 方法上是无效的（也是常见误用）。所以把事务边界放在
     * 各 public 方法上，转流逻辑本身保持纯粹。</p>
     *
     * <p>执行顺序本身就是设计：校验处理人 → 校验状态机 → 校验操作人归属 →
     * CAS 更新（影响行数判定）→ 写日志。顺序颠倒会出现"先写了日志再发现状态不对"的脏数据。</p>
     */
    private void transit(Long ticketId, TicketAction action, LoginUser operator,
                         String remark, Long newAssigneeId, boolean force) {
        Ticket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null || Integer.valueOf(1).equals(ticket.getDeleted())) {
            throw new BizException(ErrorCode.TICKET_NOT_FOUND);
        }

        TicketStatus from = TicketStatus.of(ticket.getStatus());
        TicketStatus to = action.changesStatus() ? action.getTargetStatus() : from;

        if (action.changesStatus() && !force) {
            TicketStatus.assertCanTransit(from, to);
        }
        checkOperator(action, ticket, operator);

        Long assigneeId = null;
        Long assigneeDept = null;
        if (newAssigneeId != null) {
            SysUser assignee = userMapper.selectEnabledById(newAssigneeId);
            if (assignee == null) {
                throw new BizException(ErrorCode.USER_NOT_FOUND);
            }
            assigneeId = assignee.getId();
            assigneeDept = assignee.getDeptId();
        }

        LocalDateTime now = LocalDateTime.now();
        TicketCasParam param = TicketCasParam.builder()
                .id(ticketId)
                .fromStatus(from.getCode())
                .toStatus(to.getCode())
                .version(ticket.getVersion())
                .assigneeId(assigneeId)
                .assigneeDept(assigneeDept)
                .finishTime(action == TicketAction.FINISH ? now : null)
                .closeTime(action == TicketAction.VERIFY_PASS || action == TicketAction.CLOSE ? now : null)
                .build();

        int affected = ticketMapper.casUpdate(param);
        if (affected == 0) {
            // 有人抢先改了状态或版本：让用户刷新重来，而不是静默覆盖别人的操作
            log.warn("cas conflict, ticketId={}, action={}, expectStatus={}, version={}",
                    ticketId, action, from.getCode(), ticket.getVersion());
            throw new BizException(ErrorCode.VERSION_CONFLICT);
        }

        writeFlow(ticketId, action, from, to, operator, remark);
        log.info("ticket transit ok, id={}, action={}, {}->{}, operator={}",
                ticketId, action, from.getCode(), to.getCode(), operator.getUsername());
    }

    /** 操作人归属校验：防水平越权。功能权限由 @RequirePerm 在 Controller 层管，这里只管"是不是你的单" */
    private void checkOperator(TicketAction action, Ticket ticket, LoginUser user) {
        boolean admin = isAdmin(user);
        switch (action) {
            case ACCEPT, FINISH, TRANSFER, SUSPEND, RESUME -> {
                if (!admin && !Objects.equals(ticket.getAssigneeId(), user.getId())) {
                    throw new BizException(ErrorCode.FORBIDDEN, "只有当前处理人可以执行该操作");
                }
            }
            case VERIFY_PASS, VERIFY_REJECT, REOPEN -> {
                if (!admin && !Objects.equals(ticket.getCreatorId(), user.getId())) {
                    throw new BizException(ErrorCode.FORBIDDEN, "只有提交人可以执行该操作");
                }
            }
            default -> {
                // ASSIGN / CLOSE / REJECT：功能权限点已在 Controller 层校验
            }
        }
    }

    private void writeFlow(Long ticketId, TicketAction action, TicketStatus from, TicketStatus to,
                           LoginUser operator, String remark) {
        TicketFlowLog flow = new TicketFlowLog();
        flow.setTicketId(ticketId);
        flow.setAction(action.name());
        flow.setActionDesc(action.getDesc());
        flow.setFromStatus(from.getCode());
        flow.setToStatus(to.getCode());
        flow.setOperatorId(operator.getId());
        flow.setOperatorName(operator.getRealName());
        flow.setRemark(remark);
        flowLogMapper.insert(flow);
    }

    // ============================================================ 权限与可见性

    /**
     * 取工单并校验当前用户可见。查不到与无权访问是两件事：
     * 前者 30003，后者 20004 —— 但对外都只提示"无权访问或不存在"更好（避免 ID 探测），
     * 这里为便于排查仍区分返回码，生产环境可统一成后者。
     */
    private Ticket mustGetVisible(Long id, LoginUser user) {
        Ticket t = ticketMapper.selectById(id);
        if (t == null) {
            throw new BizException(ErrorCode.TICKET_NOT_FOUND);
        }
        assertVisible(t, user);
        return t;
    }

    private void assertVisible(Ticket t, LoginUser user) {
        int scope = user.getDataScope() == null ? 1 : user.getDataScope();
        if (scope >= 4) {
            return;
        }
        if (scope == 1) {
            if (!Objects.equals(t.getCreatorId(), user.getId())
                    && !Objects.equals(t.getAssigneeId(), user.getId())) {
                throw new BizException(ErrorCode.DATA_SCOPE_DENIED);
            }
            return;
        }
        // 2=本部门 3=本部门及子部门
        if (Objects.equals(t.getCreatorDept(), user.getDeptId())
                || Objects.equals(t.getAssigneeDept(), user.getDeptId())) {
            return;
        }
        if (scope == 3) {
            // 仅当直接比对失败时才查子树，避免每次详情请求都多一次查询
            List<Long> deptIds = deptMapper.selectSelfAndChildIds(user.getDeptId());
            if (deptIds.contains(t.getCreatorDept())) {
                return;
            }
        }
        throw new BizException(ErrorCode.DATA_SCOPE_DENIED);
    }

    private boolean canSeeInternal(Ticket t, LoginUser user) {
        return isAdmin(user)
                || user.hasPerm("ticket:handle")
                || Objects.equals(t.getAssigneeId(), user.getId());
    }

    private boolean isAdmin(LoginUser user) {
        return user.getRoles() != null && user.getRoles().contains("ADMIN");
    }

    /** 当前用户在该工单上可执行的动作，供前端渲染按钮；真正的拦截仍在后端各接口 */
    private List<String> resolveActions(Ticket t, LoginUser user) {
        List<String> actions = new ArrayList<>();
        TicketStatus status = TicketStatus.of(t.getStatus());
        boolean isAssignee = Objects.equals(t.getAssigneeId(), user.getId());
        boolean isCreator = Objects.equals(t.getCreatorId(), user.getId());
        boolean admin = isAdmin(user);

        if (status == TicketStatus.PENDING_ASSIGN && user.hasPerm("ticket:assign")) {
            actions.add(TicketAction.ASSIGN.name());
        }
        if (isAssignee || admin) {
            switch (status) {
                case PENDING_PROCESS -> {
                    actions.add(TicketAction.ACCEPT.name());
                    actions.add(TicketAction.TRANSFER.name());
                    actions.add(TicketAction.SUSPEND.name());
                }
                case PROCESSING -> {
                    actions.add(TicketAction.FINISH.name());
                    actions.add(TicketAction.TRANSFER.name());
                    actions.add(TicketAction.SUSPEND.name());
                }
                case SUSPENDED -> actions.add(TicketAction.RESUME.name());
                default -> {
                }
            }
        }
        if ((isCreator || admin) && status == TicketStatus.PENDING_VERIFY) {
            actions.add(TicketAction.VERIFY_PASS.name());
            actions.add(TicketAction.VERIFY_REJECT.name());
        }
        if ((isCreator || admin) && status == TicketStatus.CLOSED) {
            actions.add(TicketAction.REOPEN.name());
        }
        if (user.hasPerm("ticket:close") && status != TicketStatus.CLOSED && status != TicketStatus.REJECTED) {
            actions.add(TicketAction.CLOSE.name());
        }
        return actions.isEmpty() ? Collections.emptyList() : actions;
    }

    private String sha256(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
