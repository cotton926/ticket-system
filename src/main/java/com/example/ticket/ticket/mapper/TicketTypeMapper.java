package com.example.ticket.ticket.mapper;

import com.example.ticket.ticket.entity.TicketType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TicketTypeMapper {

    @Select("SELECT * FROM ticket_type WHERE id = #{id} AND status = 1")
    TicketType selectById(@Param("id") Integer id);

    @Select("SELECT * FROM ticket_type WHERE status = 1 ORDER BY parent_id, id")
    List<TicketType> selectAll();
}
