package com.study.cockpit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.study.cockpit.dto.StatsVO;
import com.study.cockpit.entity.DailyTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务聚合查询。
 * 重要：拦截器不会给手写 SQL 自动追加逻辑删除与 user_id 条件，
 * 因此下列每条 SQL 都显式写出 user_id 与 deleted = 0（数据隔离的硬约束）。
 */
@Mapper
public interface DailyTaskMapper extends BaseMapper<DailyTask> {

    /** 按日聚合：本周趋势、当日完成率、本周平均完成率 */
    @Select("""
            SELECT task_date AS taskDate, COUNT(*) AS taskTotal,
                   COALESCE(SUM(CASE WHEN finish_status = 1 THEN 1 ELSE 0 END), 0) AS taskFinished,
                   COALESCE(SUM(weight), 0) AS weightTotal,
                   COALESCE(SUM(CASE WHEN finish_status = 1 THEN weight ELSE 0 END), 0) AS weightFinished
            FROM daily_task
            WHERE user_id = #{userId} AND deleted = 0
              AND task_date BETWEEN #{start} AND #{end}
            GROUP BY task_date
            ORDER BY task_date ASC
            """)
    List<StatsVO.DailyStat> selectDailyStats(@Param("userId") Long userId,
                                             @Param("start") LocalDate start,
                                             @Param("end") LocalDate end);

    /** 按日聚合（限定目标）：周报按目标筛选 */
    @Select("""
            SELECT task_date AS taskDate, COUNT(*) AS taskTotal,
                   COALESCE(SUM(CASE WHEN finish_status = 1 THEN 1 ELSE 0 END), 0) AS taskFinished,
                   COALESCE(SUM(weight), 0) AS weightTotal,
                   COALESCE(SUM(CASE WHEN finish_status = 1 THEN weight ELSE 0 END), 0) AS weightFinished
            FROM daily_task
            WHERE user_id = #{userId} AND deleted = 0 AND goal_id = #{goalId}
              AND task_date BETWEEN #{start} AND #{end}
            GROUP BY task_date
            ORDER BY task_date ASC
            """)
    List<StatsVO.DailyStat> selectDailyStatsByGoal(@Param("userId") Long userId,
                                                   @Param("goalId") Long goalId,
                                                   @Param("start") LocalDate start,
                                                   @Param("end") LocalDate end);

    /** 按目标聚合：目标进度卡片（累计到 end 为止，begin 传一个极早日期即可） */
    @Select("""
            SELECT goal_id AS goalId, COUNT(*) AS taskTotal,
                   COALESCE(SUM(weight), 0) AS weightTotal,
                   COALESCE(SUM(CASE WHEN finish_status = 1 THEN weight ELSE 0 END), 0) AS weightFinished
            FROM daily_task
            WHERE user_id = #{userId} AND deleted = 0
              AND task_date BETWEEN #{begin} AND #{end}
            GROUP BY goal_id
            """)
    List<StatsVO.GoalStat> selectGoalStats(@Param("userId") Long userId,
                                           @Param("begin") LocalDate begin,
                                           @Param("end") LocalDate end);

    /** 按科目聚合：科目完成度 */
    @Select("""
            SELECT subject_id AS subjectId, COUNT(*) AS taskTotal,
                   COALESCE(SUM(weight), 0) AS weightTotal,
                   COALESCE(SUM(CASE WHEN finish_status = 1 THEN weight ELSE 0 END), 0) AS weightFinished
            FROM daily_task
            WHERE user_id = #{userId} AND deleted = 0
              AND task_date BETWEEN #{start} AND #{end}
            GROUP BY subject_id
            """)
    List<StatsVO.SubjectStat> selectSubjectStats(@Param("userId") Long userId,
                                                 @Param("start") LocalDate start,
                                                 @Param("end") LocalDate end);

    /** 单值汇总：当日 / 区间整体完成度 */
    @Select("""
            SELECT COUNT(*) AS taskTotal,
                   COALESCE(SUM(weight), 0) AS weightTotal,
                   COALESCE(SUM(CASE WHEN finish_status = 1 THEN weight ELSE 0 END), 0) AS weightFinished
            FROM daily_task
            WHERE user_id = #{userId} AND deleted = 0
              AND task_date BETWEEN #{start} AND #{end}
            """)
    StatsVO.OverallStat selectOverall(@Param("userId") Long userId,
                                      @Param("start") LocalDate start,
                                      @Param("end") LocalDate end);

    /** 有「已完成任务」的日期，倒序返回，用于计算连续打卡天数 */
    @Select("""
            SELECT DISTINCT task_date
            FROM daily_task
            WHERE user_id = #{userId} AND deleted = 0 AND finish_status = 1
              AND task_date BETWEEN #{start} AND #{end}
            ORDER BY task_date DESC
            """)
    List<LocalDate> selectCheckinDates(@Param("userId") Long userId,
                                       @Param("start") LocalDate start,
                                       @Param("end") LocalDate end);

    /** 积压/逾期任务：未完成且（归属日已过 或 截止时间已过），命中的前端标红 */
    @Select("""
            SELECT * FROM daily_task
            WHERE user_id = #{userId} AND deleted = 0 AND finish_status = 0
              AND (task_date < #{today} OR (deadline IS NOT NULL AND deadline < #{now}))
            ORDER BY task_date ASC, id ASC
            LIMIT #{limit}
            """)
    List<DailyTask> selectBacklog(@Param("userId") Long userId,
                                  @Param("today") LocalDate today,
                                  @Param("now") LocalDateTime now,
                                  @Param("limit") int limit);

    /** 周结算用：按日聚合（与 selectDailyStats 同义，保留独立命名便于定时任务调用） */
    @Select("""
            SELECT task_date AS taskDate, COUNT(*) AS taskTotal,
                   COALESCE(SUM(CASE WHEN finish_status = 1 THEN 1 ELSE 0 END), 0) AS taskFinished,
                   COALESCE(SUM(weight), 0) AS weightTotal,
                   COALESCE(SUM(CASE WHEN finish_status = 1 THEN weight ELSE 0 END), 0) AS weightFinished
            FROM daily_task
            WHERE user_id = #{userId} AND deleted = 0 AND goal_id = #{goalId}
              AND task_date BETWEEN #{start} AND #{end}
            GROUP BY task_date
            """)
    List<StatsVO.DailyStat> selectDailyStatsForSettlement(@Param("userId") Long userId,
                                                          @Param("goalId") Long goalId,
                                                          @Param("start") LocalDate start,
                                                          @Param("end") LocalDate end);

    /** 定时任务扫描：全部用户 ID（个人部署通常只有 1 个用户） */
    @Select("SELECT DISTINCT user_id FROM daily_task WHERE deleted = 0")
    List<Long> selectAllUserIds();
}
