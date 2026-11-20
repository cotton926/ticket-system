package com.example.ticket.ticket.controller;

import com.example.ticket.common.annotation.Idempotent;
import com.example.ticket.common.annotation.RequirePerm;
import com.example.ticket.common.api.PageResult;
import com.example.ticket.common.api.Result;
import com.example.ticket.ticket.dto.AssignReq;
import com.example.ticket.ticket.dto.CommentReq;
import com.example.ticket.ticket.dto.RemarkReq;
import com.example.ticket.ticket.dto.TicketCreateReq;
import com.example.ticket.ticket.dto.TicketQueryReq;
import com.example.ticket.ticket.dto.TransferReq;
import com.example.ticket.ticket.dto.VerifyReq;
import com.example.ticket.ticket.entity.TicketComment;
import com.example.ticket.ticket.entity.TicketType;
import com.example.ticket.ticket.service.TicketService;
import com.example.ticket.ticket.vo.FlowLogVO;
import com.example.ticket.ticket.vo.TicketDetailVO;
import com.example.ticket.ticket.vo.TicketVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 工单接口。
 * <p>Controller 只做三件事：接参（@Valid）、调 Service、包 Result。不放业务 if。</p>
 */
@Tag(name = "工单")
@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @Operation(summary = "提交工单",
            description = "需要请求头 X-Idempotent-Token（先调 GET /api/v1/idempotent-token 获取）")
    @RequirePerm("ticket:create")
    @Idempotent(bizType = "TICKET_SUBMIT")
    @PostMapping
    public Result<TicketDetailVO> create(@RequestBody @Valid TicketCreateReq req) {
        return Result.ok(ticketService.submit(req));
    }

    @Operation(summary = "工单列表（支持游标分页与偏移分页）")
    @GetMapping
    public Result<PageResult<TicketVO>> page(TicketQueryReq req) {
        return Result.ok(ticketService.page(req));
    }

    @Operation(summary = "工单分类列表")
    @GetMapping("/types")
    public Result<List<TicketType>> types() {
        return Result.ok(ticketService.types());
    }

    @Operation(summary = "工单详情（含时间线与评论）")
    @GetMapping("/{id}")
    public Result<TicketDetailVO> detail(@Parameter(description = "工单ID") @PathVariable Long id) {
        return Result.ok(ticketService.detail(id));
    }

    @Operation(summary = "流转时间线")
    @GetMapping("/{id}/flows")
    public Result<List<FlowLogVO>> flows(@PathVariable Long id) {
        return Result.ok(ticketService.flows(id));
    }

    @Operation(summary = "分派工单")
    @RequirePerm("ticket:assign")
    @PutMapping("/{id}/assign")
    public Result<Void> assign(@PathVariable Long id, @RequestBody @Valid AssignReq req) {
        ticketService.assign(id, req);
        return Result.ok();
    }

    @Operation(summary = "接单")
    @RequirePerm("ticket:handle")
    @PutMapping("/{id}/accept")
    public Result<Void> accept(@PathVariable Long id) {
        ticketService.accept(id);
        return Result.ok();
    }

    @Operation(summary = "提交完成")
    @RequirePerm("ticket:handle")
    @PutMapping("/{id}/finish")
    public Result<Void> finish(@PathVariable Long id, @RequestBody(required = false) RemarkReq req) {
        ticketService.finish(id, req);
        return Result.ok();
    }

    @Operation(summary = "验收（pass=true 通过 / false 驳回，驳回必须填原因）")
    @RequirePerm("ticket:verify")
    @PutMapping("/{id}/verify")
    public Result<Void> verify(@PathVariable Long id, @RequestBody @Valid VerifyReq req) {
        ticketService.verify(id, req);
        return Result.ok();
    }

    @Operation(summary = "转派（状态不变，只换处理人）")
    @RequirePerm("ticket:handle")
    @PutMapping("/{id}/transfer")
    public Result<Void> transfer(@PathVariable Long id, @RequestBody @Valid TransferReq req) {
        ticketService.transfer(id, req);
        return Result.ok();
    }

    @Operation(summary = "挂起")
    @RequirePerm("ticket:handle")
    @PutMapping("/{id}/suspend")
    public Result<Void> suspend(@PathVariable Long id, @RequestBody(required = false) RemarkReq req) {
        ticketService.suspend(id, req);
        return Result.ok();
    }

    @Operation(summary = "恢复处理")
    @RequirePerm("ticket:handle")
    @PutMapping("/{id}/resume")
    public Result<Void> resume(@PathVariable Long id, @RequestBody(required = false) RemarkReq req) {
        ticketService.resume(id, req);
        return Result.ok();
    }

    @Operation(summary = "强制关闭")
    @RequirePerm("ticket:close")
    @PutMapping("/{id}/close")
    public Result<Void> close(@PathVariable Long id, @RequestBody(required = false) RemarkReq req) {
        ticketService.close(id, req);
        return Result.ok();
    }

    @Operation(summary = "重新打开（已关闭 7 天内）")
    @PutMapping("/{id}/reopen")
    public Result<Void> reopen(@PathVariable Long id, @RequestBody(required = false) RemarkReq req) {
        ticketService.reopen(id, req);
        return Result.ok();
    }

    @Operation(summary = "评论 / 内部备注")
    @PostMapping("/{id}/comments")
    public Result<Long> comment(@PathVariable Long id, @RequestBody @Valid CommentReq req) {
        return Result.ok(ticketService.comment(id, req));
    }

    @Operation(summary = "评论列表")
    @GetMapping("/{id}/comments")
    public Result<List<TicketComment>> comments(@PathVariable Long id) {
        return Result.ok(ticketService.comments(id));
    }
}
