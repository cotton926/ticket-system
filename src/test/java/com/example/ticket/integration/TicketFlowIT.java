package com.example.ticket.integration;

import com.example.ticket.common.context.LoginUser;
import com.example.ticket.common.context.UserContext;
import com.example.ticket.common.exception.BizException;
import com.example.ticket.common.exception.ErrorCode;
import com.example.ticket.ticket.dto.TicketQueryReq;
import com.example.ticket.ticket.service.TicketService;
import com.example.ticket.ticket.vo.TicketVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端到端集成测试：真实 MySQL + Flyway + MyBatis XML + AOP。
 *
 * <p>单元测试用 Mock 把 Mapper 全替掉了，所以下面三样东西一直是零覆盖：
 * ① Flyway 能否从空库建起 11 张表；② CAS 那条 UPDATE 在真实并发下是否真的只成功一次；
 * ③ {@code @DataScope} 切面拼出来的 SQL 是否真的把别人的单挡在外面。
 * 这三样恰好是"看起来最稳、出事最狠"的部分。</p>
 *
 * <p><b>命名成 *IT 是刻意的</b>：Surefire 默认只跑 {@code *Test}，所以 {@code mvn test} 仍然是
 * 无外部依赖的纯单元测试；要跑本类用 {@code mvn test -Dtest=TicketFlowIT}。</p>
 *
 * <p>会在独立的 {@code ticket_system_it} 库上执行 Flyway clean + migrate，
 * 由 {@link FreshSchema} 里的断言保证它绝不会指向开发库。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:mysql://127.0.0.1:3306/ticket_system_it"
                + "?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true"
                + "&serverTimezone=Asia/Shanghai&characterEncoding=utf8",
        "spring.flyway.clean-disabled=false"
})
@Import(TicketFlowIT.FreshSchema.class)
class TicketFlowIT {

    private static final String SCHEMA = "ticket_system_it";

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TicketService service;

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("Flyway 从空库建起全部业务表并写入初始化数据")
    void flyway_buildsSchemaAndSeedData() {
        Integer tables = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ?", Integer.class, SCHEMA);
        assertNotNull(tables);
        assertTrue(tables >= 11, "建表数量不足，实际 " + tables);

        Integer admin = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE username = 'admin' AND status = 1", Integer.class);
        assertEquals(1, admin, "初始化账号未写入");

        Integer types = jdbc.queryForObject("SELECT COUNT(*) FROM ticket_type", Integer.class);
        assertTrue(types != null && types > 0, "工单类型未初始化，提交会直接抛 30006");
    }

    @Test
    @DisplayName("真实并发接单：CAS 只允许一个成功，且只落一条流转日志")
    void acceptConcurrently_onlyOneWins() throws Exception {
        long id = insertTicket("IT-CAS-1", 20, 1L, 1L, 1L);

        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    UserContext.set(admin(1L));
                    startGate.await();
                    try {
                        service.accept(id);
                        return "OK";
                    } catch (BizException e) {
                        return "BIZ:" + e.getCode();
                    } finally {
                        UserContext.clear();
                    }
                }));
            }
            startGate.countDown();

            List<String> results = new ArrayList<>();
            for (Future<String> f : futures) {
                results.add(f.get(10, TimeUnit.SECONDS));
            }

            assertEquals(1, results.stream().filter("OK"::equals).count(),
                    "并发接单只能有一个成功，实际: " + results);
            // 失败方要么是 CAS 版本冲突，要么是状态机已拒绝（取决于是否抢在对方提交后读）
            String failed = results.stream().filter(r -> !"OK".equals(r)).findFirst().orElse("OK");
            assertTrue(failed.equals("BIZ:" + ErrorCode.VERSION_CONFLICT.getCode())
                            || failed.equals("BIZ:" + ErrorCode.ILLEGAL_STATUS_TRANSITION.getCode()),
                    "失败原因不合理: " + failed);
        } finally {
            pool.shutdownNow();
        }

        // 关键不变式：状态变一次、日志只能有一条。CAS 失败却写日志会留下"有流水但状态没变"的脏数据
        assertEquals(30, jdbc.queryForObject("SELECT status FROM ticket WHERE id = ?", Integer.class, id));
        assertEquals(1, jdbc.queryForObject("SELECT version FROM ticket WHERE id = ?", Integer.class, id));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM ticket_flow_log WHERE ticket_id = ? AND action = 'ACCEPT'",
                Integer.class, id));
    }

    @Test
    @DisplayName("@DataScope 真的拼进 SQL：本人范围只能看到自己提交或处理过的单")
    void dataScope_limitsRowsInRealSql() {
        insertTicket("IT-DS-1", 20, 1001L, 1L, 1001L);   // 我提交、我处理
        insertTicket("IT-DS-2", 20, 1002L, 1L, 1001L);   // 别人提交、我处理
        insertTicket("IT-DS-3", 20, 1003L, 1L, 1003L);   // 别人的单

        UserContext.set(employee(1001L, 1L));

        TicketQueryReq req = new TicketQueryReq();
        req.setTab("all");
        req.setPageNum(1);
        req.setPageSize(20);

        List<TicketVO> list = service.page(req).getList();

        assertTrue(list.stream().allMatch(v -> v.getCreatorId().equals(1001L)
                        || (v.getAssigneeId() != null && v.getAssigneeId().equals(1001L))),
                "数据权限没有生效，查到了别人的工单");
        assertEquals(2, list.size(), "本人范围应当命中 2 条，实际 " + list.size());
    }

    // ---------------------------------------------------------------- helpers

    private long insertTicket(String ticketNo, int status, Long creatorId, Long creatorDept, Long assigneeId) {
        jdbc.update("""
                INSERT INTO ticket (ticket_no, title, content, type_id, priority, status,
                                    creator_id, creator_dept, assignee_id, assignee_dept,
                                    deadline, version, deleted)
                VALUES (?, ?, ?, 1, 3, ?, ?, ?, ?, ?, DATE_ADD(NOW(), INTERVAL 24 HOUR), 0, 0)
                """, ticketNo, "集成测试-" + ticketNo, "内容", status,
                creatorId, creatorDept, assigneeId, assigneeId == null ? null : creatorDept);
        return jdbc.queryForObject("SELECT id FROM ticket WHERE ticket_no = ?", Long.class, ticketNo);
    }

    private LoginUser admin(long id) {
        return loginUser(id, 1L, 4, "ADMIN");
    }

    private LoginUser employee(long id, long deptId) {
        return loginUser(id, deptId, 1);
    }

    private LoginUser loginUser(long id, long deptId, int dataScope, String... roles) {
        LoginUser u = new LoginUser();
        u.setId(id);
        u.setUsername("it" + id);
        u.setRealName("集成用户" + id);
        u.setDeptId(deptId);
        u.setDataScope(dataScope);
        u.setRoles(Set.of(roles));
        u.setPerms(Set.of("ticket:create", "ticket:handle", "ticket:assign", "ticket:close"));
        return u;
    }

    /**
     * 每次启动把测试库清空重建，避免上一次运行残留的数据让断言飘。
     * <p>clean() 会 DROP 整库对象，所以先确认连接 URL 指向的是 {@code _it} 库 ——
     * 万一有人改了 url 指到开发库，这里必须直接失败而不是默默清库。</p>
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class FreshSchema {

        @Bean
        FlywayMigrationStrategy cleanThenMigrate(DataSource ds) {
            return flyway -> {
                try (Connection c = ds.getConnection()) {
                    String url = c.getMetaData().getURL();
                    if (!url.contains("_it")) {
                        throw new IllegalStateException("拒绝在非测试库上执行 flyway clean: " + url);
                    }
                } catch (SQLException e) {
                    throw new IllegalStateException("检查测试库连接失败", e);
                }
                flyway.clean();
                flyway.migrate();
            };
        }
    }
}
