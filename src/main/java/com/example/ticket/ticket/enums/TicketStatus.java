package com.example.ticket.ticket.enums;

import com.example.ticket.common.exception.BizException;
import com.example.ticket.common.exception.ErrorCode;
import lombok.Getter;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 工单状态机。
 *
 * <p>状态码取 0/10/20/... 的十位增量，是为了给未来留插入空间（例如之后要在
 * "待处理"与"处理中"之间加"已接单"），改枚举即可，不用动库里已有的码值。</p>
 *
 * <p><b>为什么不用工作流引擎</b>：状态只有 8 个、规则固定、且需要与工单表在同一个事务里
 * 原子更新。引擎会引入 20+ 列的运行时表和额外学习成本，收益为负。只有"流程由业务方
 * 动态定义、需要会签/并行网关"时才值得引入。</p>
 */
@Getter
public enum TicketStatus {

    DRAFT(0, "草稿"),
    PENDING_ASSIGN(10, "待分派"),
    PENDING_PROCESS(20, "待处理"),
    PROCESSING(30, "处理中"),
    PENDING_VERIFY(40, "待验收"),
    CLOSED(50, "已关闭"),
    REJECTED(60, "已驳回"),
    SUSPENDED(70, "已挂起");

    private final int code;
    private final String desc;

    TicketStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED;

    static {
        Map<TicketStatus, Set<TicketStatus>> m = new EnumMap<>(TicketStatus.class);
        m.put(DRAFT, EnumSet.of(PENDING_ASSIGN));
        m.put(PENDING_ASSIGN, EnumSet.of(PENDING_PROCESS, REJECTED));
        m.put(PENDING_PROCESS, EnumSet.of(PROCESSING, PENDING_ASSIGN, SUSPENDED, REJECTED));
        m.put(PROCESSING, EnumSet.of(PENDING_VERIFY, SUSPENDED));
        m.put(SUSPENDED, EnumSet.of(PROCESSING, PENDING_ASSIGN, REJECTED));
        m.put(PENDING_VERIFY, EnumSet.of(CLOSED, PROCESSING));
        m.put(CLOSED, EnumSet.of(PENDING_PROCESS));
        m.put(REJECTED, EnumSet.noneOf(TicketStatus.class));
        ALLOWED = Collections.unmodifiableMap(m);
    }

    private static final Map<Integer, TicketStatus> BY_CODE;

    static {
        Map<Integer, TicketStatus> m = new java.util.HashMap<>();
        for (TicketStatus s : values()) {
            m.put(s.code, s);
        }
        BY_CODE = Collections.unmodifiableMap(m);
    }

    public static TicketStatus of(Integer code) {
        TicketStatus s = code == null ? null : BY_CODE.get(code);
        if (s == null) {
            throw new BizException(ErrorCode.ILLEGAL_STATUS_TRANSITION, "未知的工单状态: " + code);
        }
        return s;
    }

    public static boolean canTransit(TicketStatus from, TicketStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return ALLOWED.getOrDefault(from, EnumSet.noneOf(TicketStatus.class)).contains(to);
    }

    /**
     * 唯一合法的流转判定入口 —— 所有 Service 必须经过它，
     * 不允许在业务代码里散落 if (status == 20 || status == 30) 这类判断。
     */
    public static void assertCanTransit(TicketStatus from, TicketStatus to) {
        if (!canTransit(from, to)) {
            throw new BizException(ErrorCode.ILLEGAL_STATUS_TRANSITION,
                    String.format("工单状态不允许从[%s]变更为[%s]", from == null ? "-" : from.desc,
                            to == null ? "-" : to.desc));
        }
    }

    public static Set<TicketStatus> nextOf(TicketStatus from) {
        return ALLOWED.getOrDefault(from, EnumSet.noneOf(TicketStatus.class));
    }

    /** 终态 = 没有任何可流转目标。注意 CLOSED 不是终态（7 天内可重开） */
    public boolean isFinal() {
        return nextOf(this).isEmpty();
    }
}
