package com.study.cockpit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 周结算快照（支撑 周日结算 / 历史归档 / 学习基线对比）。
 * goal_id = 0 表示「全部目标汇总」，避免 NULL 破坏唯一键的幂等写入。
 */
@Data
@TableName("weekly_summary")
public class WeeklySummary {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long goalId;
    private LocalDate weekStart;
    private LocalDate weekEnd;
    private Long taskTotal;
    private Long taskFinished;
    private BigDecimal weightTotal;
    private BigDecimal weightFinished;
    private BigDecimal completionRate;
    private BigDecimal avgDailyRate;
    private Long checkinDays;
    private LocalDateTime snapshotAt;
}
