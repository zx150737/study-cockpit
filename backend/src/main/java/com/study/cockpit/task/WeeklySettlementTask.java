package com.study.cockpit.task;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.study.cockpit.dto.StatsVO;
import com.study.cockpit.entity.StudyGoal;
import com.study.cockpit.entity.WeeklySummary;
import com.study.cockpit.mapper.DailyTaskMapper;
import com.study.cockpit.mapper.StudyGoalMapper;
import com.study.cockpit.mapper.WeeklySummaryMapper;
import com.study.cockpit.util.StatsCalc;
import com.study.cockpit.util.WeekUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 周日结算（需求 4：周一起始、周日结算）。
 * 每周日 23:59（UTC+8）生成 weekly_summary 快照，用于历史归档与学习基线。
 * 使用 uk_user_goal_week + ON DUPLICATE KEY UPDATE 保证幂等，可手动重跑补算。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeeklySettlementTask {

    private final DailyTaskMapper taskMapper;
    private final StudyGoalMapper goalMapper;
    private final WeeklySummaryMapper summaryMapper;

    @Scheduled(cron = "0 59 23 * * 0", zone = "Asia/Shanghai")
    public void settle() {
        LocalDate monday = WeekUtils.mondayOf(WeekUtils.today());
        LocalDate sunday = monday.plusDays(6);
        try {
            for (Long userId : taskMapper.selectAllUserIds()) {
                // goal_id = 0 表示全部目标汇总
                write(userId, 0L, monday, sunday, taskMapper.selectDailyStats(userId, monday, sunday));

                List<StudyGoal> goals = goalMapper.selectList(Wrappers.<StudyGoal>lambdaQuery()
                        .eq(StudyGoal::getUserId, userId));
                for (StudyGoal goal : goals) {
                    write(userId, goal.getId(), monday, sunday,
                            taskMapper.selectDailyStatsForSettlement(userId, goal.getId(), monday, sunday));
                }
            }
            log.info("周结算完成 week={} ~ {}", monday, sunday);
        } catch (Exception e) {
            log.error("周结算任务异常 week={}", monday, e);
        }
    }

    private void write(Long userId, Long goalId, LocalDate monday, LocalDate sunday,
                       List<StatsVO.DailyStat> dailyStats) {
        long taskTotal = 0L;
        long taskFinished = 0L;
        long checkinDays = 0L;
        BigDecimal weightTotal = BigDecimal.ZERO;
        BigDecimal weightFinished = BigDecimal.ZERO;
        List<BigDecimal> dailyRates = new ArrayList<>();

        for (StatsVO.DailyStat stat : dailyStats) {
            long total = stat.getTaskTotal() == null ? 0L : stat.getTaskTotal();
            long finished = stat.getTaskFinished() == null ? 0L : stat.getTaskFinished();
            taskTotal += total;
            taskFinished += finished;
            weightTotal = weightTotal.add(StatsCalc.nz(stat.getWeightTotal()));
            weightFinished = weightFinished.add(StatsCalc.nz(stat.getWeightFinished()));
            if (finished > 0) {
                checkinDays++;
            }
            if (total > 0) {
                dailyRates.add(StatsCalc.rate(stat.getWeightFinished(), stat.getWeightTotal()));
            }
        }

        WeeklySummary summary = new WeeklySummary();
        summary.setUserId(userId);
        summary.setGoalId(goalId);
        summary.setWeekStart(monday);
        summary.setWeekEnd(sunday);
        summary.setTaskTotal(taskTotal);
        summary.setTaskFinished(taskFinished);
        summary.setWeightTotal(weightTotal);
        summary.setWeightFinished(weightFinished);
        summary.setCompletionRate(StatsCalc.rate(weightFinished, weightTotal));
        summary.setAvgDailyRate(StatsCalc.avg(dailyRates));
        summary.setCheckinDays(checkinDays);
        summary.setSnapshotAt(WeekUtils.now());
        summaryMapper.upsert(summary);
    }
}
