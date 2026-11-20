package com.example.ticket.stat.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 统计查询。
 * <p>报表类 SQL 刻意不做实时大范围聚合：只按时间窗 + 状态聚合，
 * 且 create_time / status 都在 idx_ctime_status 里，能走索引而不是全表扫。</p>
 */
@Mapper
public interface StatMapper {

    @Select("SELECT status, COUNT(1) AS cnt FROM ticket WHERE deleted = 0 GROUP BY status")
    List<Map<String, Object>> countByStatus();

    @Select("SELECT COUNT(1) FROM ticket WHERE deleted = 0")
    long countAll();

    @Select("""
            SELECT COUNT(1) FROM ticket
            WHERE deleted = 0 AND create_time >= #{start} AND create_time < #{end}
            """)
    long countCreatedBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT COUNT(1) FROM ticket WHERE deleted = 0 AND timeout_flag = 1")
    long countTimeout();

    @Select("""
            SELECT ROUND(AVG(TIMESTAMPDIFF(MINUTE, assign_time, finish_time)))
            FROM ticket
            WHERE deleted = 0 AND assign_time IS NOT NULL AND finish_time IS NOT NULL
              AND create_time >= #{start}
            """)
    Double avgCostMinutes(@Param("start") LocalDateTime start);

    /**
     * 处理人待办负载 Top N。
     *
     * <p>LEFT JOIN sys_user 取姓名：只 join 结果集这 10 行（LIMIT 在分组之后生效，
     * 但组数就是处理人数，量级极小），不会像关联大表那样放大扫描行数。
     * 用 LEFT JOIN 而非 INNER JOIN，用户被删时负载行仍保留，只是姓名为空。</p>
     */
    @Select("""
            SELECT t.assignee_id AS assigneeId, u.real_name AS assigneeName, COUNT(1) AS cnt
            FROM ticket t
            LEFT JOIN sys_user u ON u.id = t.assignee_id
            WHERE t.deleted = 0 AND t.assignee_id IS NOT NULL
              AND t.status IN (20, 30)
            GROUP BY t.assignee_id, u.real_name
            ORDER BY cnt DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> topAssigneeLoad(@Param("limit") int limit);
}
