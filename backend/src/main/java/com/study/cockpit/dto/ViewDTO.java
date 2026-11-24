package com.study.cockpit.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 驾驶舱 / 周报 / 归档 / 站内提醒的响应对象 */
public class ViewDTO {

    /** 主驾驶舱首页：一次请求返回全部指标 */
    @Data
    public static class OverviewVO {
        /** 当日整体完成率(%) */
        private BigDecimal todayRate;
        private Boolean hasTask;
        /** 本周趋势（周一至周日，未来日期 hasTask=false 且 rate=null） */
        private List<TrendPoint> weeklyTrend;
        private List<GoalCard> goalCards;
        /** 连续打卡天数 */
        private Integer streakDays;
        private BacklogPanel backlog;
        /** 本周平均完成率(%) */
        private BigDecimal weeklyAvgRate;
        private Baseline baseline;
    }

    @Data
    public static class TrendPoint {
        private LocalDate date;
        private BigDecimal rate;
        private Boolean hasTask;
    }

    @Data
    public static class GoalCard {
        private Long goalId;
        private String name;
        /** 累计完成率(%) */
        private BigDecimal completionRate;
        /** 本周完成率(%) */
        private BigDecimal weekRate;
        private Long taskTotal;
    }

    @Data
    public static class BacklogPanel {
        private Long total;
        private List<BacklogItem> items;
    }

    @Data
    public static class BacklogItem {
        private Long taskId;
        private String title;
        private String goalName;
        private String subjectName;
        private LocalDate taskDate;
        private LocalDateTime deadline;
        private Long overdueDays;
    }

    /** 学习基线：最近 4 个已结算周均值 vs 本周 */
    @Data
    public static class Baseline {
        private Boolean available;
        private BigDecimal baselineRate;
        private BigDecimal currentRate;
        private BigDecimal diff;
        private BigDecimal improvementPct;
        private Integer sampleWeeks;
    }

    // ---------------- 周度分析 ----------------

    @Data
    public static class WeeklyVO {
        private LocalDate weekStart;
        private LocalDate weekEnd;
        private BigDecimal completionRate;
        private BigDecimal avgDailyRate;
        private Long taskTotal;
        private List<TrendPoint> dailyTrend;
        private List<SubjectRate> subjectRates;
    }

    @Data
    public static class SubjectRate {
        private Long subjectId;
        private String name;
        private BigDecimal completionRate;
    }

    @Data
    public static class WeekQuery {
        /** 周一日期，必填；后端按 UTC+8 计算周日 */
        @NotNull(message = "weekStart 不能为空")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate weekStart;
        private Long goalId;
    }

    // ---------------- 归档 ----------------

    @Data
    public static class WeekVO {
        private Long id;
        private LocalDate weekStart;
        private LocalDate weekEnd;
        private BigDecimal completionRate;
        private BigDecimal avgDailyRate;
        private Long checkinDays;
        private Long taskTotal;
        private Long taskFinished;
        private LocalDateTime snapshotAt;
    }

    @Data
    public static class WeekDetailVO {
        private WeekVO summary;
        private List<BizDTO.TaskVO> tasks;
    }

    @Data
    public static class ArchiveTaskQuery {
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate weekStart;
        private Long goalId;
        private Integer finishStatus;
        @Min(value = 1, message = "page 最小为 1")
        private Long page = 1L;
        @Max(value = 100, message = "size 最大为 100")
        private Long size = 20L;
    }

    // ---------------- 提醒 ----------------

    @Data
    public static class OverdueVO {
        private Long total;
        private List<BacklogItem> items;
    }
}
