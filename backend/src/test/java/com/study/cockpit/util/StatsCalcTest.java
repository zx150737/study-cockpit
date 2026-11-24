package com.study.cockpit.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 完成度与打卡算法单元测试。
 * 纯工具类，不依赖数据库与 Spring 上下文，可独立运行。
 */
class StatsCalcTest {

    @Test
    void rate_shouldReturnZero_whenTotalIsZero() {
        BigDecimal rate = StatsCalc.rate(BigDecimal.TEN, BigDecimal.ZERO);
        assertEquals(0, BigDecimal.ZERO.compareTo(rate));
    }

    @Test
    void rate_shouldComputeWeightedPercent() {
        BigDecimal rate = StatsCalc.rate(new BigDecimal("3"), new BigDecimal("4"));
        assertEquals(new BigDecimal("75.00"), rate);
    }

    @Test
    void rate_shouldRoundHalfUpToTwoDecimals() {
        // 2/3 = 66.666... -> 66.67
        BigDecimal rate = StatsCalc.rate(new BigDecimal("2"), new BigDecimal("3"));
        assertEquals(new BigDecimal("66.67"), rate);
    }

    @Test
    void avg_shouldIgnoreEmptyList() {
        assertEquals(0, BigDecimal.ZERO.compareTo(StatsCalc.avg(List.of())));
    }

    @Test
    void avg_shouldArithmeticMeanNonNull() {
        BigDecimal avg = StatsCalc.avg(List.of(new BigDecimal("80"), new BigDecimal("60")));
        assertEquals(new BigDecimal("70.00"), avg);
    }

    @Test
    void streakDays_shouldCountBackFromToday_whenCheckedInToday() {
        // 11-23 周一 ~ 11-24 周二连续打卡，今日 11-24
        List<java.time.LocalDate> desc = List.of(
                java.time.LocalDate.of(2026, 11, 24),
                java.time.LocalDate.of(2026, 11, 23));
        assertEquals(2, StatsCalc.streakDays(desc, java.time.LocalDate.of(2026, 11, 24)));
    }

    @Test
    void streakDays_shouldStartYesterday_whenTodayNotCheckedIn() {
        // 今日未打卡，昨日及之前连续两天
        List<java.time.LocalDate> desc = List.of(
                java.time.LocalDate.of(2026, 11, 23),
                java.time.LocalDate.of(2026, 11, 22));
        assertEquals(2, StatsCalc.streakDays(desc, java.time.LocalDate.of(2026, 11, 24)));
    }

    @Test
    void streakDays_shouldBeZero_whenEmpty() {
        assertEquals(0, StatsCalc.streakDays(List.of(), java.time.LocalDate.of(2026, 11, 24)));
    }

    @Test
    void improvementPct_shouldReturnHundred_whenBaselineZero() {
        // 既有实现：基线为 0（暂无基线）时返回 100，前端据此展示「暂无基线」
        assertEquals(new BigDecimal("100.00"),
                StatsCalc.improvementPct(BigDecimal.ZERO, new BigDecimal("80")));
    }

    @Test
    void nz_shouldTreatNullAsZero() {
        assertEquals(0, BigDecimal.ZERO.compareTo(StatsCalc.nz(null)));
    }
}
