package com.study.cockpit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 每日任务表。
 * task_date 为归属日（UTC+8，用于日/周聚合）；deadline 为截止时刻（用于积压判定）。
 * goal_id 冗余存储，使按目标筛选/导出/归档无需 JOIN。
 */
@Data
@TableName("daily_task")
public class DailyTask {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long goalId;
    private Long subjectId;
    private String title;
    private BigDecimal weight;
    private LocalDate taskDate;
    private LocalDateTime deadline;
    /** 掌握度 1-5，允许为空 */
    private Integer mastery;
    private String reviewNote;
    /** 0 未完成 / 1 已完成 */
    private Integer finishStatus;
    private LocalDateTime finishedAt;
    @TableLogic(value = "0", delval = "1")
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
