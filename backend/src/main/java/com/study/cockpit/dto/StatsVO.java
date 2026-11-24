package com.study.cockpit.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 聚合查询结果载体。所有统计都在 SQL 层按 user_id + 日期范围过滤并分组，
 * 禁止把全表数据捞回应用层再过滤。
 */
public class StatsVO {

    /** 按日聚合（本周趋势 / 平均完成率 / 打卡） */
    @Data
    public static class DailyStat {
        private LocalDate taskDate;
        private Long taskTotal;
        private Long taskFinished;
        private BigDecimal weightTotal;
        private BigDecimal weightFinished;
    }

    /** 按目标聚合（目标进度卡片） */
    @Data
    public static class GoalStat {
        private Long goalId;
        private Long taskTotal;
        private BigDecimal weightTotal;
        private BigDecimal weightFinished;
    }

    /** 按科目聚合（科目完成度） */
    @Data
    public static class SubjectStat {
        private Long subjectId;
        private Long taskTotal;
        private BigDecimal weightTotal;
        private BigDecimal weightFinished;
    }

    /** 单值汇总（当日 / 指定区间整体完成度） */
    @Data
    public static class OverallStat {
        private Long taskTotal;
        private BigDecimal weightTotal;
        private BigDecimal weightFinished;
    }

    /** CSV 导出用的图片数量统计 */
    @Data
    public static class ImageCount {
        private Long taskId;
        private Long imageCount;
    }
}
