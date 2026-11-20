package com.example.ticket.ticket.service;

import com.example.ticket.common.api.PageResult;
import com.example.ticket.ticket.dto.AssignReq;
import com.example.ticket.ticket.dto.CommentReq;
import com.example.ticket.ticket.dto.RemarkReq;
import com.example.ticket.ticket.dto.TicketCreateReq;
import com.example.ticket.ticket.dto.TicketQueryReq;
import com.example.ticket.ticket.dto.TransferReq;
import com.example.ticket.ticket.dto.VerifyReq;
import com.example.ticket.ticket.entity.TicketComment;
import com.example.ticket.ticket.vo.FlowLogVO;
import com.example.ticket.ticket.vo.TicketDetailVO;
import com.example.ticket.ticket.vo.TicketVO;

import java.util.List;

public interface TicketService {

    TicketDetailVO submit(TicketCreateReq req);

    PageResult<TicketVO> page(TicketQueryReq req);

    TicketDetailVO detail(Long id);

    List<FlowLogVO> flows(Long id);

    void assign(Long id, AssignReq req);

    void accept(Long id);

    void finish(Long id, RemarkReq req);

    void verify(Long id, VerifyReq req);

    void transfer(Long id, TransferReq req);

    void suspend(Long id, RemarkReq req);

    void resume(Long id, RemarkReq req);

    void close(Long id, RemarkReq req);

    void reopen(Long id, RemarkReq req);

    Long comment(Long id, CommentReq req);

    List<TicketComment> comments(Long id);

    /** 工单分类（含 SLA 时长），前端提交页下拉用 */
    List<com.example.ticket.ticket.entity.TicketType> types();
}
