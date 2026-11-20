package com.example.ticket.common.idempotent;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 幂等兜底记录（第 2 层防线）。
 *
 * <p>{@code uk_biz} 唯一索引是整条幂等链路的最后一道防线：Redis 挂了、令牌提前过期、
 * 并发绕过切面，都会在这里被 {@link org.springframework.dao.DuplicateKeyException} 拦下，
 * 由全局异常处理转成 30002「请勿重复提交」。</p>
 */
@Mapper
public interface IdempotentRecordMapper {

    @Insert("""
            INSERT INTO idempotent_record (biz_type, biz_key, request_hash)
            VALUES (#{bizType}, #{bizKey}, #{requestHash})
            """)
    int insert(@Param("bizType") String bizType,
               @Param("bizKey") String bizKey,
               @Param("requestHash") String requestHash);

    /** 分批删除，避免一次大事务把表锁住 */
    @Delete("DELETE FROM idempotent_record WHERE create_time < #{before} LIMIT 5000")
    int deleteBefore(@Param("before") LocalDateTime before);
}
