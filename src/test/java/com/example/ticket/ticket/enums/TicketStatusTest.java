package com.example.ticket.ticket.enums;

import com.example.ticket.common.exception.BizException;
import com.example.ticket.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 状态机校验。
 *
 * <p><b>预期值必须写死在这里，不能从 ALLOWED / nextOf() 反推。</b>
 * 如果 oracle 取自被测代码本身（{@code nextOf(from).contains(to)}），改了流转表预期会跟着变，
 * 测试永远通过 —— 那就只测到了"抛异常时错误码对不对"，对流转表本身零约束。
 * 本类的 {@link #EXPECTED} 是业务约定的人工副本，改流转表就必须同步改它，这是刻意的重复。</p>
 *
 * <p>另有一条只靠"写死"抓不到的缺陷：新增状态但忘了在 ALLOWED 里配规则时，
 * {@code getOrDefault} 会返回空集合，该状态被静默当成终态（所有流转被拒），
 * 所以 {@link #isFinal_onlyRejected()} 用"只有 REJECTED 是终态"来兜这条。</p>
 */
class TicketStatusTest {

    /** 业务约定的流转表 —— 人工维护，与 TicketStatus.ALLOWED 互为校验 */
    private static final Map<TicketStatus, Set<TicketStatus>> EXPECTED = Map.of(
            TicketStatus.DRAFT, EnumSet.of(TicketStatus.PENDING_ASSIGN),
            TicketStatus.PENDING_ASSIGN, EnumSet.of(TicketStatus.PENDING_PROCESS, TicketStatus.REJECTED),
            TicketStatus.PENDING_PROCESS, EnumSet.of(TicketStatus.PROCESSING, TicketStatus.PENDING_ASSIGN,
                    TicketStatus.SUSPENDED, TicketStatus.REJECTED),
            TicketStatus.PROCESSING, EnumSet.of(TicketStatus.PENDING_VERIFY, TicketStatus.SUSPENDED),
            TicketStatus.PENDING_VERIFY, EnumSet.of(TicketStatus.CLOSED, TicketStatus.PROCESSING),
            TicketStatus.CLOSED, EnumSet.of(TicketStatus.PENDING_PROCESS),
            TicketStatus.REJECTED, EnumSet.noneOf(TicketStatus.class),
            TicketStatus.SUSPENDED, EnumSet.of(TicketStatus.PROCESSING, TicketStatus.PENDING_ASSIGN,
                    TicketStatus.REJECTED)
    );

    private static Set<TicketStatus> expectedNextOf(TicketStatus from) {
        return EXPECTED.getOrDefault(from, EnumSet.noneOf(TicketStatus.class));
    }

    static Stream<Arguments> allPairs() {
        return Stream.of(TicketStatus.values())
                .flatMap(from -> Stream.of(TicketStatus.values())
                        .map(to -> Arguments.of(from, to)));
    }

    @Test
    @DisplayName("流转表与业务约定一致（改动必须同步改 EXPECTED）")
    void transitionTable_matchesSpec() {
        // 新增枚举值却忘了写预期，会在这里直接失败，而不是被 getOrDefault 静默放过
        assertEquals(TicketStatus.values().length, EXPECTED.size(), "EXPECTED 未覆盖全部状态");
        for (TicketStatus from : TicketStatus.values()) {
            assertTrue(EXPECTED.containsKey(from), from + " 未在 EXPECTED 中声明");
            assertEquals(expectedNextOf(from), TicketStatus.nextOf(from),
                    from + " 的出边与业务约定不一致");
        }
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("allPairs")
    @DisplayName("全组合：合法放行、非法抛 30001")
    void assertCanTransit_allPairs(TicketStatus from, TicketStatus to) {
        if (expectedNextOf(from).contains(to)) {
            assertDoesNotThrow(() -> TicketStatus.assertCanTransit(from, to),
                    from + " → " + to + " 应当合法");
        } else {
            BizException e = assertThrows(BizException.class,
                    () -> TicketStatus.assertCanTransit(from, to),
                    from + " → " + to + " 应当被拦截");
            assertEquals(ErrorCode.ILLEGAL_STATUS_TRANSITION.getCode(), e.getCode());
        }
    }

    @Test
    @DisplayName("只有 REJECTED 是终态（新增状态漏配规则时会被这条抓住）")
    void isFinal_onlyRejected() {
        for (TicketStatus s : TicketStatus.values()) {
            assertEquals(s == TicketStatus.REJECTED, s.isFinal(),
                    s + " 的终态判定不对：漏配出边会让新状态被静默当成终态");
        }
    }

    @Test
    @DisplayName("已关闭不是终态：7 天内可重开，所以不能进终态集合")
    void closed_isNotFinal() {
        assertFalse(TicketStatus.CLOSED.isFinal());
        assertDoesNotThrow(() -> TicketStatus.assertCanTransit(
                TicketStatus.CLOSED, TicketStatus.PENDING_PROCESS));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 8, 55, 999})
    @DisplayName("未知状态码不能静默通过")
    void of_unknownCode_throws(int code) {
        BizException e = assertThrows(BizException.class, () -> TicketStatus.of(code));
        assertEquals(ErrorCode.ILLEGAL_STATUS_TRANSITION.getCode(), e.getCode());
    }

    @Test
    @DisplayName("status 为 null（脏数据）也必须抛业务异常，不能返回 null 往下走")
    void of_null_throws() {
        BizException e = assertThrows(BizException.class, () -> TicketStatus.of(null));
        assertEquals(ErrorCode.ILLEGAL_STATUS_TRANSITION.getCode(), e.getCode());
    }

    @Test
    @DisplayName("流转任一端为 null 一律拒绝")
    void assertCanTransit_nullEndpoints_rejected() {
        assertThrows(BizException.class,
                () -> TicketStatus.assertCanTransit(null, TicketStatus.PENDING_ASSIGN));
        assertThrows(BizException.class,
                () -> TicketStatus.assertCanTransit(TicketStatus.DRAFT, null));
        assertThrows(BizException.class,
                () -> TicketStatus.assertCanTransit(null, null));
    }
}
