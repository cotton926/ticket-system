package com.example.ticket.common.api;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 分页响应。
 * <p>total 允许为 null —— 非首页关闭 count 统计（PageHelper 的 count 在深分页 + 多条件场景下
 * 往往比主查询还慢），前端据 {@code total == null} 判断"不显示总页数"。</p>
 */
@Data
@AllArgsConstructor
public class PageResult<T> {

    private List<T> list;
    private Long total;
    private int pageNum;
    private int pageSize;
    /** 游标分页：本页最后一条的 createTime，用于请求下一页 */
    private String nextCursorTime;
    private Long nextCursorId;

    public static <T> PageResult<T> of(List<T> list, long total, int pageNum, int pageSize) {
        return new PageResult<>(list, total, pageNum, pageSize, null, null);
    }

    public static <T> PageResult<T> cursor(List<T> list, int pageSize, String nextTime, Long nextId) {
        return new PageResult<>(list, null, 0, pageSize, nextTime, nextId);
    }
}
