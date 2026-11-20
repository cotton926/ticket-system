package com.example.ticket.ticket.dto;

import com.example.ticket.common.scope.ScopedQuery;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 工单列表查询条件。
 *
 * <p>三种分页方式共存，由参数组合决定：
 * <ul>
 *   <li>传 {@code lastCreateTime} → 游标分页（推荐，恒定耗时，天然不漂移）</li>
 *   <li>只传 {@code pageNum} → 偏移分页（跳页用，深页时代价高）</li>
 *   <li>{@code needTotal=false} → 不执行 count，省掉一次全量扫描</li>
 * </ul>
 * </p>
 */
@Data
public class TicketQueryReq implements ScopedQuery {

    /** created=我提交的 todo=待我处理 dept=本部门 all=全部（实际范围仍受数据权限约束） */
    private String tab = "created";

    private List<Integer> status;

    private Integer typeId;

    private Integer priority;

    /** 仅支持工单号/标题的**前缀**匹配；%keyword% 无法走索引，是全表扫的隐形来源 */
    private String keyword;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    private Integer pageNum = 1;

    private Integer pageSize = 20;

    /** 是否统计总数（首页需要，翻页不需要） */
    private Boolean needTotal = Boolean.TRUE;

    /** 游标：上一页最后一条的 createTime + id，双字段游标解决同秒数据漏读 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastCreateTime;

    private Long lastId;

    /* ---------------------------------------------------------------
     * 以下字段只由服务端填充，@JsonIgnore 保证客户端传了也无效
     * --------------------------------------------------------------- */

    @JsonIgnore
    private Long creatorId;

    @JsonIgnore
    private Long assigneeId;

    /** 数据权限片段，由 @DataScope 切面注入 */
    @JsonIgnore
    private String dataScope;

    @JsonIgnore
    private LocalDateTime startTime;

    @JsonIgnore
    private LocalDateTime endTime;

    @JsonIgnore
    private Integer offset;

    /** 日期左闭右开：endDate + 1 天，避免 23:59:59 漏掉带毫秒的数据 */
    public void normalizeTimeRange() {
        this.startTime = startDate == null ? null : startDate.atStartOfDay();
        this.endTime = endDate == null ? null : endDate.plusDays(1).atStartOfDay();
    }

    public void applyOffset() {
        int pn = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int ps = pageSize == null || pageSize < 1 ? 20 : pageSize;
        this.pageNum = pn;
        this.pageSize = ps;
        this.offset = (pn - 1) * ps;
    }

    public boolean isCursorMode() {
        return lastCreateTime != null;
    }
}
