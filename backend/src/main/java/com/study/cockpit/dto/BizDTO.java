package com.study.cockpit.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 目标 / 科目 / 任务 / 图片的请求与响应对象 */
public class BizDTO {

    // ---------------- 目标 ----------------

    @Data
    public static class GoalReq {
        @NotBlank(message = "目标名称不能为空")
        @Size(max = 128, message = "目标名称过长")
        private String name;
    }

    @Data
    public static class GoalVO {
        private Long id;
        private String name;
        /** 累计加权完成率(%) */
        private BigDecimal completionRate;
        private Integer subjectCount;
        private LocalDateTime createdAt;
    }

    // ---------------- 科目 ----------------

    @Data
    public static class SubjectReq {
        @NotNull(message = "goalId 不能为空")
        private Long goalId;

        @NotBlank(message = "科目名称不能为空")
        @Size(max = 128, message = "科目名称过长")
        private String name;

        @NotNull(message = "默认权重不能为空")
        @DecimalMin(value = "0.01", message = "权重必须大于 0")
        private BigDecimal defaultWeight;
    }

    @Data
    public static class SubjectVO {
        private Long id;
        private Long goalId;
        private String name;
        private BigDecimal defaultWeight;
        /** 本周科目完成率(%) */
        private BigDecimal completionRate;
    }

    // ---------------- 任务 ----------------

    @Data
    public static class TaskReq {
        @NotNull(message = "goalId 不能为空")
        private Long goalId;

        @NotNull(message = "subjectId 不能为空")
        private Long subjectId;

        @NotBlank(message = "任务内容不能为空")
        @Size(max = 255, message = "任务内容过长")
        private String title;

        /** 不传时取科目默认权重 */
        @DecimalMin(value = "0.01", message = "权重必须大于 0")
        private BigDecimal weight;

        @NotNull(message = "任务日期不能为空")
        private LocalDate taskDate;

        private LocalDateTime deadline;

        @Min(value = 1, message = "掌握度需在 1~5 之间")
        @Max(value = 5, message = "掌握度需在 1~5 之间")
        private Integer mastery;

        @Size(max = 1000, message = "复盘备注过长")
        private String reviewNote;
    }

    @Data
    public static class TaskQuery {
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate taskDate;
        /** 周一日期 yyyy-MM-dd，与 taskDate 二选一 */
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate weekStart;
        private Long goalId;
        private Long subjectId;
        /** 0 未完成 / 1 已完成 */
        private Integer finishStatus;
        /** true 时只看积压/逾期任务 */
        private Boolean overdue;
        @Min(value = 1, message = "page 最小为 1")
        private Long page = 1L;
        @Max(value = 100, message = "size 最大为 100")
        private Long size = 20L;
    }

    @Data
    public static class FinishReq {
        @NotNull(message = "finishStatus 不能为空")
        @Min(value = 0, message = "finishStatus 只能是 0 或 1")
        @Max(value = 1, message = "finishStatus 只能是 0 或 1")
        private Integer finishStatus;
    }

    @Data
    public static class TaskVO {
        private Long id;
        private Long goalId;
        private String goalName;
        private Long subjectId;
        private String subjectName;
        private String title;
        private BigDecimal weight;
        private LocalDate taskDate;
        private LocalDateTime deadline;
        private Integer mastery;
        private String reviewNote;
        private Integer finishStatus;
        private LocalDateTime finishedAt;
        /** 未完成且已过期/逾期 -> 前端标红 */
        private Boolean overdue;
        private Integer imageCount;
        private LocalDateTime createdAt;
    }

    // ---------------- 图片 ----------------

    @Data
    public static class ImageVO {
        private Long id;
        private Long taskId;
        private String originalName;
        /** 带 HMAC 签名的临时访问地址（短有效期） */
        private String temporaryUrl;
        private LocalDateTime uploadTime;
        private LocalDateTime expireTime;
        private Long fileSize;
        private String mimeType;
    }
}
