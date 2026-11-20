package com.example.ticket.ticket.mapper;

import com.example.ticket.ticket.entity.TicketComment;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TicketCommentMapper {

    @Insert("""
            INSERT INTO ticket_comment (ticket_id, user_id, user_name, content, is_internal)
            VALUES (#{ticketId}, #{userId}, #{userName}, #{content}, #{isInternal})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(TicketComment comment);

    /** includeInternal=false 时过滤掉内部备注，防止处理人的内部沟通被提交人看到 */
    @Select("""
            <script>
            SELECT id, ticket_id, user_id, user_name, content, is_internal, create_time
            FROM ticket_comment
            WHERE ticket_id = #{ticketId}
            <if test="includeInternal == false">
                AND is_internal = 0
            </if>
            ORDER BY create_time ASC, id ASC
            </script>
            """)
    List<TicketComment> selectByTicketId(@Param("ticketId") Long ticketId,
                                        @Param("includeInternal") boolean includeInternal);
}
