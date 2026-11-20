package com.example.ticket.ticket.mapper;

import com.example.ticket.ticket.dto.TicketCasParam;
import com.example.ticket.ticket.dto.TicketQueryReq;
import com.example.ticket.ticket.entity.Ticket;
import com.example.ticket.ticket.vo.TicketVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工单 Mapper。复杂动态 SQL 放 XML（需要精确控制索引命中），简单查询用注解。
 */
@Mapper
public interface TicketMapper {

    Ticket selectById(@Param("id") Long id);

    int insert(Ticket ticket);

    List<TicketVO> selectPage(TicketQueryReq req);

    long countPage(TicketQueryReq req);

    /**
     * 唯一的写入状态入口：CAS 更新。
     * <p>WHERE 同时带 fromStatus 与 version，影响行数为 0 即表示被并发抢先修改。
     * 一条语句完成"检查并设置"，没有 select-then-update 的竞态窗口。</p>
     */
    int casUpdate(TicketCasParam param);

    /** 取当日最大工单号，用于进程重启后恢复序列（唯一索引才是最终保证） */
    String selectMaxTicketNo(@Param("prefix") String prefix);

    /** 超时扫描：只取未标记过的，靠 idx_status_dept_ctime 少扫一半 */
    List<Ticket> selectTimeoutCandidates(@Param("now") LocalDateTime now, @Param("limit") int limit);

    int markTimeout(@Param("ids") List<Long> ids);
}
