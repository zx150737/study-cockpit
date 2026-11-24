package com.study.cockpit.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.study.cockpit.dto.StatsVO;
import com.study.cockpit.dto.ViewDTO;
import com.study.cockpit.entity.StudyGoal;
import com.study.cockpit.mapper.DailyTaskMapper;
import com.study.cockpit.mapper.StudyGoalMapper;
import com.study.cockpit.mapper.WeeklySummaryMapper;
import com.study.cockpit.util.StatsCalc;
import com.study.cockpit.util.WeekUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 主驾驶舱首页聚合（需求 5）。
 * 一次请求返回：当日完成率、本周趋势、目标进度卡片、连续打卡、
 * 积压预警、本周平均完成率、学习基线对比。
 * 全部统计在 SQL 层按 user_id + 日期范围过滤后聚合，不在应用层捞全表。
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final int BASELINE_WEEKS = 4;

    private final DailyTaskMapper taskMapper;
    private final StudyGoalMapper goalMapper;
    private final WeeklySummaryMapper summaryMapper;
    private final TaskService taskService;

    public ViewDTO.OverviewVO overview(Long userId) {
        LocalDate today = WeekUtils.today();
        LocalDate monday = WeekUtils.mondayOf(today);
        LocalDate sunday = monday.plusDays(6);

        ViewDTO.OverviewVO vo = new ViewDTO.OverviewVO();

        // 1) 当日整体完成率（环形图）
        StatsVO.OverallStat todayStat = taskMapper.selectOverall(userId, today, today);
        vo.setTodayRate(StatsCalc.rate(todayStat.getWeightFinished(), todayStat.getWeightTotal()));
        vo.setHasTask(todayStat.getTaskTotal() != null && todayStat.getTaskTotal() > 0);

        // 2) 本周趋势折线（周一至周日 7 点，未来日期不画 0）
        List<StatsVO.DailyStat> dailyStats = taskMapper.selectDailyStats(userId, monday, sunday);
        Map<LocalDate, StatsVO.DailyStat> statMap = new HashMap<>();
        for (StatsVO.DailyStat stat : dailyStats) {
            statMap.put(stat.getTaskDate(), stat);
        }
        List<ViewDTO.TrendPoint> trend = new ArrayList<>();
        List<BigDecimal> ratesWithTask = new ArrayList<>();
        for (LocalDate date : WeekUtils.weekDays(monday)) {
            ViewDTO.TrendPoint point = new ViewDTO.TrendPoint();
            point.setDate(date);
            if (date.isAfter(today)) {
                point.setRate(null);
                point.setHasTask(false);
            } else {
                StatsVO.DailyStat stat = statMap.get(date);
                if (stat == null || stat.getTaskTotal() == null || stat.getTaskTotal() == 0) {
                    point.setRate(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                    point.setHasTask(false);
                } else {
                    BigDecimal rate = StatsCalc.rate(stat.getWeightFinished(), stat.getWeightTotal());
                    point.setRate(rate);
                    point.setHasTask(true);
                    ratesWithTask.add(rate);
                }
            }
            trend.add(point);
        }
        vo.setWeeklyTrend(trend);

        // 3) 本周平均完成率（仅有任务日计入）
        vo.setWeeklyAvgRate(StatsCalc.avg(ratesWithTask));

        // 4) 各备考目标进度卡片
        Map<Long, StatsVO.GoalStat> cumulativeMap = new HashMap<>();
        for (StatsVO.GoalStat stat : taskMapper.selectGoalStats(userId, StatsCalc.earliest(), today)) {
            cumulativeMap.put(stat.getGoalId(), stat);
        }
        Map<Long, StatsVO.GoalStat> weekMap = new HashMap<>();
        for (StatsVO.GoalStat stat : taskMapper.selectGoalStats(userId, monday, sunday)) {
            weekMap.put(stat.getGoalId(), stat);
        }
        List<StudyGoal> goals = goalMapper.selectList(Wrappers.<StudyGoal>lambdaQuery()
                .eq(StudyGoal::getUserId, userId)
                .orderByAsc(StudyGoal::getId));
        List<ViewDTO.GoalCard> cards = new ArrayList<>();
        for (StudyGoal goal : goals) {
            ViewDTO.GoalCard card = new ViewDTO.GoalCard();
            card.setGoalId(goal.getId());
            card.setName(goal.getName());
            StatsVO.GoalStat cumulative = cumulativeMap.get(goal.getId());
            StatsVO.GoalStat week = weekMap.get(goal.getId());
            card.setCompletionRate(cumulative == null ? StatsCalc.rate(null, null)
                    : StatsCalc.rate(cumulative.getWeightFinished(), cumulative.getWeightTotal()));
            card.setWeekRate(week == null ? StatsCalc.rate(null, null)
                    : StatsCalc.rate(week.getWeightFinished(), week.getWeightTotal()));
            card.setTaskTotal(cumulative == null ? 0L : cumulative.getTaskTotal());
            cards.add(card);
        }
        vo.setGoalCards(cards);

        // 5) 连续打卡天数
        List<LocalDate> checkins = taskMapper.selectCheckinDates(userId, today.minusDays(365), today);
        vo.setStreakDays(StatsCalc.streakDays(checkins, today));

        // 6) 积压任务预警面板
        ViewDTO.OverdueVO overdue = taskService.overdue(userId, 10);
        ViewDTO.BacklogPanel panel = new ViewDTO.BacklogPanel();
        panel.setTotal(overdue.getTotal());
        panel.setItems(overdue.getItems());
        vo.setBacklog(panel);

        // 7) 学习基线对比：最近 4 个已结算周均值 vs 本周当前完成率
        List<BigDecimal> baseRates = summaryMapper.selectBaselineRates(userId, monday, BASELINE_WEEKS);
        StatsVO.OverallStat weekOverall = taskMapper.selectOverall(userId, monday, sunday);
        BigDecimal currentRate = StatsCalc.rate(weekOverall.getWeightFinished(), weekOverall.getWeightTotal());
        ViewDTO.Baseline baseline = new ViewDTO.Baseline();
        baseline.setCurrentRate(currentRate);
        baseline.setSampleWeeks(baseRates.size());
        if (baseRates.isEmpty()) {
            baseline.setAvailable(false);
            baseline.setBaselineRate(null);
            baseline.setDiff(null);
            baseline.setImprovementPct(null);
        } else {
            BigDecimal base = StatsCalc.avg(baseRates);
            baseline.setAvailable(true);
            baseline.setBaselineRate(base);
            baseline.setDiff(currentRate.subtract(base).setScale(2, RoundingMode.HALF_UP));
            baseline.setImprovementPct(StatsCalc.improvementPct(base, currentRate));
        }
        vo.setBaseline(baseline);

        return vo;
    }
}
