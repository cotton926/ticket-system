package com.example.ticket.ticket.mapper;

import com.example.ticket.ticket.entity.TicketFlowLog;
import com.example.ticket.ticket.vo.FlowLogVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 流转日志 Mapper。
 *
 * <p><b>约定：本 Mapper 的 insert 只允许被 TicketServiceImpl.transit 调用。</b>
 * 任何绕过统一入口直接插日志的写法都会破坏"状态变了必有日志"的强一致，
 * 由 ArchUnit 单测兜底检查。</p>
 */
@Mapper
public interface TicketFlowLogMapper {

    @Insert("""
            INSERT INTO ticket_flow_log
                (ticket_id, action, action_desc, from_status, to_status, operator_id, operator_name, remark)
            VALUES
                (#{ticketId}, #{action}, #{actionDesc}, #{fromStatus}, #{toStatus},
                 #{operatorId}, #{operatorName}, #{remark})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(TicketFlowLog log);

    /** 走 idx_ticket_ctime，单次查询返回整条时间线，无 N+1 */
    @Select("""
            SELECT id, ticket_id, action, action_desc, from_status, to_status,
                   operator_id, operator_name, remark, create_time
            FROM ticket_flow_log
            WHERE ticket_id = #{ticketId}
            ORDER BY create_time ASC, id ASC
            """)
    List<FlowLogVO> selectByTicketId(@Param("ticketId") Long ticketId);
}
