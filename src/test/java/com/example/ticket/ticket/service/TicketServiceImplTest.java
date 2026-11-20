package com.example.ticket.ticket.service;

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
import com.example.ticket.ticket.dto.TicketCasParam;
import com.example.ticket.ticket.dto.TicketCreateReq;
import com.example.ticket.ticket.dto.TicketQueryReq;
import com.example.ticket.ticket.dto.VerifyReq;
import com.example.ticket.ticket.entity.Ticket;
import com.example.ticket.ticket.entity.TicketFlowLog;
import com.example.ticket.ticket.entity.TicketType;
import com.example.ticket.ticket.enums.TicketStatus;
import com.example.ticket.ticket.mapper.TicketCommentMapper;
import com.example.ticket.ticket.mapper.TicketFlowLogMapper;
import com.example.ticket.ticket.mapper.TicketMapper;
import com.example.ticket.ticket.mapper.TicketTypeMapper;
import com.example.ticket.ticket.service.impl.TicketServiceImpl;
import com.example.ticket.ticket.vo.TicketDetailVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 工单 Service 的关键分支测试。
 *
 * <p>覆盖四类最容易出线上事故的地方：状态机被绕过、CAS 冲突被静默忽略、
 * 查不到数据抛 NPE、越权（功能越权与数据越权）。</p>
 *
 * <p><b>身份一律在用例里显式声明</b>，不在 {@code @BeforeEach} 里给一个默认账号 ——
 * 默认给 admin 的话，任何一个漏改身份的用例都会"因为有权而通过"，
 * 越权用例的失败就变成了沉默的假绿。</p>
 */
@ExtendWith(MockitoExtension.class)
class TicketServiceImplTest {

    // ---- 身份与部门常量：裸数字 1/2/3/4 在断言里无法自解释 ----
    private static final long ADMIN_ID = 100L;
    private static final long DISPATCHER_ID = 101L;
    private static final long CREATOR_ID = 200L;
    private static final long ASSIGNEE_ID = 300L;
    private static final long OUTSIDER_ID = 301L;
    private static final long DEPT_SALES = 3L;
    private static final long DEPT_IT = 4L;
    /** 数据范围：1 本人 / 2 本部门 / 3 本部门及子部门 / 4 全部。生产侧建议提成枚举 */
    private static final int SCOPE_SELF = 1;
    private static final int SCOPE_DEPT = 2;
    private static final int SCOPE_ALL = 4;

    private static final Set<String> ALL_PERMS =
            Set.of("ticket:create", "ticket:handle", "ticket:assign", "ticket:close");

    @Mock
    private TicketMapper ticketMapper;
    @Mock
    private TicketFlowLogMapper flowLogMapper;
    @Mock
    private TicketCommentMapper commentMapper;
    @Mock
    private TicketTypeMapper typeMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private SysDeptMapper deptMapper;
    @Mock
    private IdempotentRecordMapper idempotentRecordMapper;
    @Mock
    private TicketNoGenerator ticketNoGenerator;

    @InjectMocks
    private TicketServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "reopenDays", 7);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
        // 也是 static ThreadLocal：只清 UserContext 会把幂等键漏给下一个用例
        IdempotentContext.clear();
    }

    // ================================================================ 分派

    @Nested
    @DisplayName("分派")
    class Assign {

        @Test
        @DisplayName("成功：CAS 参数带读取时的版本快照，并落一条 ASSIGN 日志")
        void assign_success_writesFlowLogAndCasParam() {
            asAdmin();
            when(ticketMapper.selectById(1L)).thenReturn(
                    ticket(1L, TicketStatus.PENDING_ASSIGN, 5, CREATOR_ID, null));
            when(userMapper.selectEnabledById(ASSIGNEE_ID)).thenReturn(employee(ASSIGNEE_ID, DEPT_IT));
            when(ticketMapper.casUpdate(any())).thenReturn(1);

            service.assign(1L, assignReq(ASSIGNEE_ID));

            ArgumentCaptor<TicketCasParam> casCaptor = ArgumentCaptor.forClass(TicketCasParam.class);
            verify(ticketMapper).casUpdate(casCaptor.capture());
            TicketCasParam cas = casCaptor.getValue();
            assertEquals(TicketStatus.PENDING_ASSIGN.getCode(), cas.getFromStatus().intValue());
            assertEquals(TicketStatus.PENDING_PROCESS.getCode(), cas.getToStatus().intValue());
            assertEquals(5, cas.getVersion().intValue(), "版本号必须用读取时的快照，不能重查");
            assertEquals(ASSIGNEE_ID, cas.getAssigneeId().longValue());
            assertEquals(DEPT_IT, cas.getAssigneeDept().longValue(), "处理人部门快照必须一并写入");

            ArgumentCaptor<TicketFlowLog> logCaptor = ArgumentCaptor.forClass(TicketFlowLog.class);
            verify(flowLogMapper).insert(logCaptor.capture());
            TicketFlowLog log = logCaptor.getValue();
            assertEquals("ASSIGN", log.getAction());
            assertEquals(1L, log.getTicketId().longValue());
            assertEquals(TicketStatus.PENDING_PROCESS.getCode(), log.getToStatus().intValue());
            assertEquals(ADMIN_ID, log.getOperatorId().longValue());
        }

        @Test
        @DisplayName("状态非法：抛 30001，不落库、不写日志，且状态校验早于查询处理人")
        void assign_illegalStatus_throwsAndNoWrite() {
            asAdmin();
            when(ticketMapper.selectById(1L)).thenReturn(
                    ticket(1L, TicketStatus.PENDING_PROCESS, 0, CREATOR_ID, ASSIGNEE_ID));

            BizException ex = assertThrows(BizException.class, () -> service.assign(1L, assignReq(ASSIGNEE_ID)));

            assertEquals(ErrorCode.ILLEGAL_STATUS_TRANSITION.getCode(), ex.getCode());
            verify(ticketMapper, never()).casUpdate(any());
            verify(flowLogMapper, never()).insert(any());
            // 先判状态再查人：状态不对就不该产生任何数据库往返
            verify(userMapper, never()).selectEnabledById(anyLong());
        }

        @Test
        @DisplayName("CAS 返回 0 行：抛 40001，且绝不写流转日志")
        void assign_casConflict_throwsVersionConflict() {
            asAdmin();
            when(ticketMapper.selectById(1L)).thenReturn(
                    ticket(1L, TicketStatus.PENDING_ASSIGN, 3, CREATOR_ID, null));
            when(userMapper.selectEnabledById(ASSIGNEE_ID)).thenReturn(employee(ASSIGNEE_ID, DEPT_IT));
            when(ticketMapper.casUpdate(any())).thenReturn(0);

            BizException ex = assertThrows(BizException.class, () -> service.assign(1L, assignReq(ASSIGNEE_ID)));

            assertEquals(ErrorCode.VERSION_CONFLICT.getCode(), ex.getCode());
            // 冲突时写日志会出现"有流转记录但状态没变"的脏数据
            verify(flowLogMapper, never()).insert(any());
        }

        @Test
        @DisplayName("处理人不存在：抛 30004，不能派出脏单")
        void assign_assigneeNotFound_throws() {
            asAdmin();
            when(ticketMapper.selectById(1L)).thenReturn(
                    ticket(1L, TicketStatus.PENDING_ASSIGN, 0, CREATOR_ID, null));
            when(userMapper.selectEnabledById(ASSIGNEE_ID)).thenReturn(null);

            BizException ex = assertThrows(BizException.class, () -> service.assign(1L, assignReq(ASSIGNEE_ID)));

            assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), ex.getCode());
            verify(ticketMapper, never()).casUpdate(any());
        }

        @Test
        @DisplayName("调度越界：可见但提交人不是本部门的单，不允许分派")
        void assign_byDispatcherOutOfDept_denied() {
            // 本部门处理人的单可见（assigneeDept 命中），但提交人是别的部门 -> 不能派
            asDispatcher(DEPT_SALES);
            Ticket t = ticket(1L, TicketStatus.PENDING_ASSIGN, 0, CREATOR_ID, ASSIGNEE_ID);
            t.setCreatorDept(DEPT_IT);
            t.setAssigneeDept(DEPT_SALES);
            when(ticketMapper.selectById(1L)).thenReturn(t);

            BizException ex = assertThrows(BizException.class, () -> service.assign(1L, assignReq(ASSIGNEE_ID)));

            assertEquals(ErrorCode.DATA_SCOPE_DENIED.getCode(), ex.getCode());
            verify(ticketMapper, never()).casUpdate(any());
        }

        @Test
        @DisplayName("软删工单：抛 30003，不能被重新分派")
        void assign_onDeletedTicket_throwsNotFound() {
            asAdmin();
            Ticket t = ticket(1L, TicketStatus.PENDING_ASSIGN, 0, CREATOR_ID, null);
            t.setDeleted(1);
            when(ticketMapper.selectById(1L)).thenReturn(t);

            BizException ex = assertThrows(BizException.class, () -> service.assign(1L, assignReq(ASSIGNEE_ID)));

            assertEquals(ErrorCode.TICKET_NOT_FOUND.getCode(), ex.getCode());
            verify(ticketMapper, never()).casUpdate(any());
        }
    }

    // ================================================================ 详情与数据权限

    @Nested
    @DisplayName("详情与数据权限")
    class Detail {

        @Test
        @DisplayName("工单不存在：抛 30003，而不是抛 NPE")
        void detail_notFound_throwsBizException() {
            asAdmin();
            when(ticketMapper.selectById(999L)).thenReturn(null);

            BizException ex = assertThrows(BizException.class, () -> service.detail(999L));

            assertEquals(ErrorCode.TICKET_NOT_FOUND.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("本人范围：看别人的工单抛 20004（水平越权）")
        void detail_otherUserTicket_denied() {
            asEmployee(OUTSIDER_ID, DEPT_SALES);
            when(ticketMapper.selectById(1L)).thenReturn(
                    ticket(1L, TicketStatus.PROCESSING, 0, CREATOR_ID, ASSIGNEE_ID));

            BizException ex = assertThrows(BizException.class, () -> service.detail(1L));

            assertEquals(ErrorCode.DATA_SCOPE_DENIED.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("本部门范围：同部门工单可见")
        void detail_sameDept_visible() {
            asDeptScope(DEPT_SALES);
            Ticket t = ticket(1L, TicketStatus.PROCESSING, 0, CREATOR_ID, ASSIGNEE_ID);
            t.setCreatorDept(DEPT_SALES);
            when(ticketMapper.selectById(1L)).thenReturn(t);

            TicketDetailVO vo = service.detail(1L);

            assertEquals(1L, vo.getId().longValue());
        }

        @Test
        @DisplayName("提交人待验收时能看到「验收通过 / 驳回」")
        void detail_creatorSeesVerifyActions() {
            asEmployee(CREATOR_ID, DEPT_SALES);
            when(ticketMapper.selectById(1L)).thenReturn(
                    ticket(1L, TicketStatus.PENDING_VERIFY, 0, CREATOR_ID, ASSIGNEE_ID));

            List<String> actions = service.detail(1L).getAvailableActions();

            assertTrue(actions.contains("VERIFY_PASS"), "提交人应能验收，实际: " + actions);
            assertTrue(actions.contains("VERIFY_REJECT"));
        }

        @Test
        @DisplayName("处理人在待验收时看不到验收按钮（不能自己验自己）")
        void detail_assigneeHasNoVerifyAction() {
            asEmployee(ASSIGNEE_ID, DEPT_IT, Set.of("ticket:handle"));
            when(ticketMapper.selectById(1L)).thenReturn(
                    ticket(1L, TicketStatus.PENDING_VERIFY, 0, CREATOR_ID, ASSIGNEE_ID));

            List<String> actions = service.detail(1L).getAvailableActions();

            assertFalse(actions.contains("VERIFY_PASS"), "处理人不应看到验收按钮，实际: " + actions);
            assertFalse(actions.contains("VERIFY_REJECT"));
        }
    }

    // ================================================================ 流转归属

    @Nested
    @DisplayName("流转归属校验")
    class Operator {

        @Test
        @DisplayName("提交人自己接单：可见但非处理人，抛 20003")
        void accept_byNonAssignee_forbidden() {
            // 注意这里必须用「可见但角色不对」的身份：若用无关员工，
            // 会先被 mustGetVisible 拦成 20004，测不到 checkOperator 这段
            asEmployee(CREATOR_ID, DEPT_SALES);
            when(ticketMapper.selectById(1L)).thenReturn(
                    ticket(1L, TicketStatus.PENDING_PROCESS, 0, CREATOR_ID, ASSIGNEE_ID));

            BizException ex = assertThrows(BizException.class, () -> service.accept(1L));

            assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
            verify(ticketMapper, never()).casUpdate(any());
            verify(flowLogMapper, never()).insert(any());
        }

        @Test
        @DisplayName("处理人自己验收：抛 20003")
        void verify_byAssignee_forbidden() {
            asEmployee(ASSIGNEE_ID, DEPT_IT);
            when(ticketMapper.selectById(1L)).thenReturn(
                    ticket(1L, TicketStatus.PENDING_VERIFY, 0, CREATOR_ID, ASSIGNEE_ID));

            VerifyReq req = new VerifyReq();
            req.setPass(true);

            BizException ex = assertThrows(BizException.class, () -> service.verify(1L, req));

            assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
            verify(ticketMapper, never()).casUpdate(any());
        }

        @Test
        @DisplayName("处理人完成工单：写 finishTime，不写 closeTime")
        void finish_byAssignee_setsFinishTime() {
            asEmployee(ASSIGNEE_ID, DEPT_IT);
            when(ticketMapper.selectById(1L)).thenReturn(
                    ticket(1L, TicketStatus.PROCESSING, 2, CREATOR_ID, ASSIGNEE_ID));
            when(ticketMapper.casUpdate(any())).thenReturn(1);

            service.finish(1L, null);

            ArgumentCaptor<TicketCasParam> captor = ArgumentCaptor.forClass(TicketCasParam.class);
            verify(ticketMapper).casUpdate(captor.capture());
            TicketCasParam cas = captor.getValue();
            assertEquals(TicketStatus.PENDING_VERIFY.getCode(), cas.getToStatus().intValue());
            assertNotNull(cas.getFinishTime(), "完成时间必须落库，统计工时靠它");
            assertNull(cas.getCloseTime(), "完成不等于关闭");
        }
    }

    // ================================================================ 重开

    @Nested
    @DisplayName("重开")
    class Reopen {

        @Test
        @DisplayName("超过 7 天：抛 30007，不能重开")
        void reopen_expired_throws() {
            asEmployee(CREATOR_ID, DEPT_SALES);
            Ticket t = ticket(1L, TicketStatus.CLOSED, 0, CREATOR_ID, ASSIGNEE_ID);
            t.setCloseTime(LocalDateTime.now().minusDays(8));
            when(ticketMapper.selectById(1L)).thenReturn(t);

            BizException ex = assertThrows(BizException.class, () -> service.reopen(1L, null));

            assertEquals(ErrorCode.REOPEN_EXPIRED.getCode(), ex.getCode());
            verify(ticketMapper, never()).casUpdate(any());
        }

        @Test
        @DisplayName("窗口内：已关闭回到待处理，并记 REOPEN 日志")
        void reopen_withinWindow_ok() {
            asEmployee(CREATOR_ID, DEPT_SALES);
            Ticket t = ticket(1L, TicketStatus.CLOSED, 0, CREATOR_ID, ASSIGNEE_ID);
            t.setCloseTime(LocalDateTime.now().minusDays(1));
            when(ticketMapper.selectById(1L)).thenReturn(t);
            when(ticketMapper.casUpdate(any())).thenReturn(1);

            assertDoesNotThrow(() -> service.reopen(1L, null));

            ArgumentCaptor<TicketFlowLog> logCaptor = ArgumentCaptor.forClass(TicketFlowLog.class);
            verify(flowLogMapper).insert(logCaptor.capture());
            assertEquals("REOPEN", logCaptor.getValue().getAction());
            assertEquals(TicketStatus.CLOSED.getCode(), logCaptor.getValue().getFromStatus().intValue());
            assertEquals(TicketStatus.PENDING_PROCESS.getCode(), logCaptor.getValue().getToStatus().intValue());
        }
    }

    // ================================================================ 提交

    @Nested
    @DisplayName("提交")
    class Submit {

        @Test
        @DisplayName("成功：写幂等记录 + SUBMIT 日志，状态直接进待分派")
        void submit_success_writesIdempotentRecordAndFlow() {
            asEmployee(CREATOR_ID, DEPT_SALES);
            IdempotentContext.set("tok-1");
            when(typeMapper.selectById(1)).thenReturn(ticketType(1, 4));
            when(ticketNoGenerator.next()).thenReturn("TK20261119000099");
            when(ticketMapper.insert(any())).thenAnswer(inv -> {
                ((Ticket) inv.getArgument(0)).setId(9L);
                return 1;
            });
            when(ticketMapper.selectById(9L)).thenReturn(
                    ticket(9L, TicketStatus.PENDING_ASSIGN, 0, CREATOR_ID, null));

            TicketDetailVO vo = service.submit(createReq());

            assertEquals(9L, vo.getId().longValue());

            ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
            verify(idempotentRecordMapper).insert(eq("TICKET_SUBMIT"), eq("tok-1"), hash.capture());
            assertTrue(hash.getValue().matches("[0-9a-f]{64}"), "请求指纹应是 sha256，实际: " + hash.getValue());

            ArgumentCaptor<TicketFlowLog> logCaptor = ArgumentCaptor.forClass(TicketFlowLog.class);
            verify(flowLogMapper).insert(logCaptor.capture());
            assertEquals("SUBMIT", logCaptor.getValue().getAction());
            assertEquals(TicketStatus.DRAFT.getCode(), logCaptor.getValue().getFromStatus().intValue());
            assertEquals(TicketStatus.PENDING_ASSIGN.getCode(), logCaptor.getValue().getToStatus().intValue());
        }

        @Test
        @DisplayName("工单类型不存在：抛 30006")
        void submit_unknownType_throws() {
            asEmployee(CREATOR_ID, DEPT_SALES);
            when(typeMapper.selectById(999)).thenReturn(null);

            BizException ex = assertThrows(BizException.class, () -> service.submit(createReq(999, 3)));

            assertEquals(ErrorCode.TICKET_TYPE_NOT_FOUND.getCode(), ex.getCode());
            verify(ticketMapper, never()).insert(any());
        }

        @Test
        @DisplayName("类型没配 SLA：抛 30005，不能提交出没有时限的单")
        void submit_slaMissing_throws() {
            asEmployee(CREATOR_ID, DEPT_SALES);
            when(typeMapper.selectById(1)).thenReturn(ticketType(1, null));

            BizException ex = assertThrows(BizException.class, () -> service.submit(createReq()));

            assertEquals(ErrorCode.SLA_CONFIG_MISSING.getCode(), ex.getCode());
            verify(ticketMapper, never()).insert(any());
        }

        @Test
        @DisplayName("幂等记录唯一键冲突：抛 30002（第二层兜底）")
        void submit_duplicateIdempotentRecord_throwsRepeatSubmit() {
            asEmployee(CREATOR_ID, DEPT_SALES);
            IdempotentContext.set("tok-1");
            when(typeMapper.selectById(1)).thenReturn(ticketType(1, 4));
            when(idempotentRecordMapper.insert(anyString(), anyString(), anyString()))
                    .thenThrow(new DuplicateKeyException("uk_biz"));

            BizException ex = assertThrows(BizException.class, () -> service.submit(createReq()));

            assertEquals(ErrorCode.REPEAT_SUBMIT.getCode(), ex.getCode());
            verify(ticketMapper, never()).insert(any());
        }

        @Test
        @DisplayName("工单号冲突：换号重试后成功，不误报成重复提交")
        void submit_insertRetry_thenSuccess() {
            asEmployee(CREATOR_ID, DEPT_SALES);
            when(typeMapper.selectById(1)).thenReturn(ticketType(1, 4));
            when(ticketNoGenerator.next()).thenReturn("TK-A", "TK-B");
            when(ticketMapper.insert(any()))
                    .thenThrow(new DuplicateKeyException("uk_ticket_no"))
                    .thenAnswer(inv -> {
                        ((Ticket) inv.getArgument(0)).setId(9L);
                        return 1;
                    });
            when(ticketMapper.selectById(9L)).thenReturn(
                    ticket(9L, TicketStatus.PENDING_ASSIGN, 0, CREATOR_ID, null));

            TicketDetailVO vo = service.submit(createReq());

            assertEquals(9L, vo.getId().longValue());
            verify(ticketMapper, times(2)).insert(any());
            verify(ticketNoGenerator, times(2)).next();
        }

        @Test
        @DisplayName("工单号连续冲突 3 次：抛 30002，不能无限重试")
        void submit_insertRetryExhausted_throwsRepeatSubmit() {
            asEmployee(CREATOR_ID, DEPT_SALES);
            when(typeMapper.selectById(1)).thenReturn(ticketType(1, 4));
            when(ticketNoGenerator.next()).thenReturn("TK-X");
            when(ticketMapper.insert(any())).thenThrow(new DuplicateKeyException("uk_ticket_no"));

            BizException ex = assertThrows(BizException.class, () -> service.submit(createReq()));

            assertEquals(ErrorCode.REPEAT_SUBMIT.getCode(), ex.getCode());
            verify(ticketMapper, times(3)).insert(any());
        }
    }

    // ================================================================ 分页查询

    @Nested
    @DisplayName("分页查询")
    class Page {

        @Test
        @DisplayName("pageSize 越界：抛 10003，不产生任何查询（防拖库）")
        void page_pageSizeExceeded_throws() {
            asEmployee(ASSIGNEE_ID, DEPT_IT);
            TicketQueryReq req = new TicketQueryReq();
            req.setPageSize(100000);

            BizException ex = assertThrows(BizException.class, () -> service.page(req));

            assertEquals(ErrorCode.PAGE_SIZE_EXCEED.getCode(), ex.getCode());
            verify(ticketMapper, never()).selectPage(any());
            verify(ticketMapper, never()).countPage(any());
        }

        @Test
        @DisplayName("tab=todo：归属人一律取自登录态，客户端传的 assigneeId 无效")
        void page_todoTab_forcesAssigneeFromLoginUser() {
            asEmployee(ASSIGNEE_ID, DEPT_IT);
            TicketQueryReq req = new TicketQueryReq();
            req.setTab("todo");
            req.setAssigneeId(99999L);   // 伪造别人的待办
            when(ticketMapper.selectPage(any())).thenReturn(List.of());

            service.page(req);

            ArgumentCaptor<TicketQueryReq> captor = ArgumentCaptor.forClass(TicketQueryReq.class);
            verify(ticketMapper).selectPage(captor.capture());
            TicketQueryReq actual = captor.getValue();
            assertEquals(ASSIGNEE_ID, actual.getAssigneeId().longValue());
            assertTrue(actual.getStatus().contains(TicketStatus.PENDING_PROCESS.getCode()));
            assertTrue(actual.getStatus().contains(TicketStatus.PROCESSING.getCode()));
            assertFalse(actual.getStatus().contains(TicketStatus.SUSPENDED.getCode()), "挂起不算待办");
        }

        @Test
        @DisplayName("needTotal=false：跳过 count，深分页下省掉最慢的那次查询")
        void page_needTotalFalse_skipsCount() {
            asEmployee(ASSIGNEE_ID, DEPT_IT);
            TicketQueryReq req = new TicketQueryReq();
            req.setNeedTotal(Boolean.FALSE);
            when(ticketMapper.selectPage(any())).thenReturn(List.of());

            service.page(req);

            verify(ticketMapper, never()).countPage(any());
        }
    }

    // ---------------------------------------------------------------- helpers

    private void asAdmin() {
        UserContext.set(loginUser(ADMIN_ID, DEPT_IT, SCOPE_ALL, ALL_PERMS, "ADMIN"));
    }

    private void asEmployee(long id, long deptId) {
        UserContext.set(loginUser(id, deptId, SCOPE_SELF, Set.of("ticket:create", "ticket:handle")));
    }

    private void asEmployee(long id, long deptId, Set<String> perms) {
        UserContext.set(loginUser(id, deptId, SCOPE_SELF, perms));
    }

    private void asDispatcher(long deptId) {
        UserContext.set(loginUser(DISPATCHER_ID, deptId, SCOPE_DEPT,
                Set.of("ticket:assign", "ticket:close"), "DISPATCHER"));
    }

    private void asDeptScope(long deptId) {
        UserContext.set(loginUser(DISPATCHER_ID, deptId, SCOPE_DEPT, ALL_PERMS, "DISPATCHER"));
    }

    private LoginUser loginUser(long id, long deptId, int dataScope, Set<String> perms, String... roles) {
        LoginUser u = new LoginUser();
        u.setId(id);
        u.setUsername("u" + id);
        u.setRealName("用户" + id);
        u.setDeptId(deptId);
        u.setDataScope(dataScope);
        u.setPerms(perms);
        u.setRoles(Set.of(roles));
        return u;
    }

    private Ticket ticket(long id, TicketStatus status, int version, Long creatorId, Long assigneeId) {
        Ticket t = new Ticket();
        t.setId(id);
        t.setTicketNo("TK20261118000001");
        t.setTitle("测试工单");
        t.setStatus(status.getCode());
        t.setVersion(version);
        t.setCreatorId(creatorId);
        t.setCreatorDept(DEPT_SALES);
        t.setAssigneeId(assigneeId);
        t.setAssigneeDept(assigneeId == null ? null : DEPT_IT);
        t.setDeleted(0);
        return t;
    }

    private SysUser employee(long id, long deptId) {
        SysUser u = new SysUser();
        u.setId(id);
        u.setRealName("李工");
        u.setDeptId(deptId);
        u.setStatus(1);
        return u;
    }

    private TicketType ticketType(int id, Integer slaHours) {
        TicketType t = new TicketType();
        t.setId(id);
        t.setName("故障报修");
        t.setSlaHours(slaHours);
        t.setStatus(1);
        return t;
    }

    private AssignReq assignReq(long assigneeId) {
        AssignReq req = new AssignReq();
        req.setAssigneeId(assigneeId);
        req.setRemark("按负载分派");
        return req;
    }

    private TicketCreateReq createReq() {
        return createReq(1, 3);
    }

    private TicketCreateReq createReq(Integer typeId, Integer priority) {
        TicketCreateReq req = new TicketCreateReq();
        req.setTitle("打印机坏了");
        req.setContent("三楼打印机卡纸");
        req.setTypeId(typeId);
        req.setPriority(priority);
        return req;
    }
}
