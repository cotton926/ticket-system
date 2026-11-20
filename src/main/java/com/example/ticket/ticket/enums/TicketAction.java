package com.example.ticket.ticket.enums;

import lombok.Getter;

/**
 * 工单动作：承载"动作 → 目标状态 + 中文描述"的映射。
 *
 * <p>新增一个动作只需要在这里加一行，Service 的 transit 逻辑不用改 —— 这是把
 * "状态机的规则" 与 "业务编排" 解耦的关键。TRANSFER 的目标状态为 null，
 * 表示它不改变状态（只换处理人），属于特例，由 Service 单独分支处理。</p>
 */
@Getter
public enum TicketAction {

    SUBMIT(TicketStatus.PENDING_ASSIGN, "提交工单"),
    ASSIGN(TicketStatus.PENDING_PROCESS, "分派"),
    ACCEPT(TicketStatus.PROCESSING, "接单"),
    FINISH(TicketStatus.PENDING_VERIFY, "提交完成"),
    VERIFY_PASS(TicketStatus.CLOSED, "验收通过"),
    VERIFY_REJECT(TicketStatus.PROCESSING, "验收驳回"),
    TRANSFER(null, "转派"),
    SUSPEND(TicketStatus.SUSPENDED, "挂起"),
    RESUME(TicketStatus.PROCESSING, "恢复处理"),
    CLOSE(TicketStatus.CLOSED, "强制关闭"),
    REJECT(TicketStatus.REJECTED, "驳回"),
    REOPEN(TicketStatus.PENDING_PROCESS, "重新打开");

    private final TicketStatus targetStatus;
    private final String desc;

    TicketAction(TicketStatus targetStatus, String desc) {
        this.targetStatus = targetStatus;
        this.desc = desc;
    }

    public boolean changesStatus() {
        return targetStatus != null;
    }
}
