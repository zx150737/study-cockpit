package com.study.cockpit.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 完成度与打卡算法（口径唯一来源，避免各页面算法不一致）。
 * 完成率 = Σ(权重 × 是否完成) / Σ(权重) × 100，分母为 0 时返回 0。
 */
public final class StatsCalc {

    public static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final LocalDate EARLIEST = LocalDate.of(1970, 1, 1);

    private StatsCalc() {
    }

    public static LocalDate earliest() {
        return EARLIEST;
    }

    /** null 安全求和 */
    public static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** 加权完成率(%)，保留 2 位小数 */
    public static BigDecimal rate(BigDecimal weightFinished, BigDecimal weightTotal) {
        if (weightTotal == null || weightTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal finished = weightFinished == null ? BigDecimal.ZERO : weightFinished;
        return finished.multiply(HUNDRED)
                .divide(weightTotal, 2, RoundingMode.HALF_UP);
    }

    /** 有任务日的完成率算术平均（无任务日不计入，避免拉低本周均值） */
    public static BigDecimal avg(List<BigDecimal> rates) {
        if (rates == null || rates.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal r : rates) {
            sum = sum.add(r == null ? BigDecimal.ZERO : r);
        }
        return sum.divide(new BigDecimal(rates.size()), 2, RoundingMode.HALF_UP);
    }

    /**
     * 连续打卡天数。
     * 今日已有完成任务则从今日算起；否则从昨日算起（避免当天还没学习就显示断签）。
     * checkinDatesDesc 为倒序的「有已完成任务」日期集合。
     */
    public static int streakDays(List<LocalDate> checkinDatesDesc, LocalDate today) {
        if (checkinDatesDesc == null || checkinDatesDesc.isEmpty()) {
            return 0;
        }
        Set<LocalDate> set = new HashSet<>(checkinDatesDesc);
        LocalDate cursor = set.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        while (set.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    /** 相对提升百分比，基线为 0 时返回 null（前端显示"暂无基线"） */
    public static BigDecimal improvementPct(BigDecimal baseline, BigDecimal current) {
        if (baseline == null || baseline.compareTo(BigDecimal.ZERO) <= 0) {
            return HUNDRED.setScale(2, RoundingMode.HALF_UP);
        }
        return current.subtract(baseline).multiply(HUNDRED)
                .divide(baseline, 2, RoundingMode.HALF_UP);
    }
}
