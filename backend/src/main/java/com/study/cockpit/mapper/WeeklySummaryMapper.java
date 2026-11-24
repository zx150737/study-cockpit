package com.study.cockpit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.study.cockpit.entity.WeeklySummary;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface WeeklySummaryMapper extends BaseMapper<WeeklySummary> {

    /** 学习基线：最近 N 个已结算周（不含本周）的整体完成率 */
    @Select("""
            SELECT completion_rate FROM weekly_summary
            WHERE user_id = #{userId} AND goal_id = 0 AND week_start < #{weekStart}
            ORDER BY week_start DESC
            LIMIT #{limit}
            """)
    List<BigDecimal> selectBaselineRates(@Param("userId") Long userId,
                                         @Param("weekStart") LocalDate weekStart,
                                         @Param("limit") int limit);

    /** 周日结算幂等写入（uk_user_goal_week 保证重复执行不产生重复行） */
    @Insert("""
            INSERT INTO weekly_summary
              (user_id, goal_id, week_start, week_end, task_total, task_finished,
               weight_total, weight_finished, completion_rate, avg_daily_rate, checkin_days, snapshot_at)
            VALUES
              (#{e.userId}, #{e.goalId}, #{e.weekStart}, #{e.weekEnd}, #{e.taskTotal}, #{e.taskFinished},
               #{e.weightTotal}, #{e.weightFinished}, #{e.completionRate}, #{e.avgDailyRate}, #{e.checkinDays}, #{e.snapshotAt})
            ON DUPLICATE KEY UPDATE
               task_total = VALUES(task_total),
               task_finished = VALUES(task_finished),
               weight_total = VALUES(weight_total),
               weight_finished = VALUES(weight_finished),
               completion_rate = VALUES(completion_rate),
               avg_daily_rate = VALUES(avg_daily_rate),
               checkin_days = VALUES(checkin_days),
               snapshot_at = VALUES(snapshot_at)
            """)
    int upsert(@Param("e") WeeklySummary summary);
}
