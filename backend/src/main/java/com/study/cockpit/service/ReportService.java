package com.study.cockpit.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.study.cockpit.common.Result;
import com.study.cockpit.dto.BizDTO;
import com.study.cockpit.dto.StatsVO;
import com.study.cockpit.dto.ViewDTO;
import com.study.cockpit.entity.DailyTask;
import com.study.cockpit.entity.StudyGoal;
import com.study.cockpit.entity.StudySubject;
import com.study.cockpit.entity.WeeklySummary;
import com.study.cockpit.mapper.DailyTaskMapper;
import com.study.cockpit.mapper.StudyGoalMapper;
import com.study.cockpit.mapper.StudySubjectMapper;
import com.study.cockpit.mapper.TaskImageMapper;
import com.study.cockpit.mapper.WeeklySummaryMapper;
import com.study.cockpit.util.StatsCalc;
import com.study.cockpit.util.WeekUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 周度数据分析 + CSV 导出 + 历史归档（需求 6 与页面 5/6）。
 * 导出不打包图片，仅输出 [IMAGE] 标记与图片数量；文件名只由校验后的日期拼接。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final DailyTaskMapper taskMapper;
    private final TaskImageMapper imageMapper;
    private final StudySubjectMapper subjectMapper;
    private final StudyGoalMapper goalMapper;
    private final WeeklySummaryMapper summaryMapper;
    private final TaskService taskService;

    // ---------------- 周度分析 ----------------

    public ViewDTO.WeeklyVO weekly(Long userId, ViewDTO.WeekQuery query) {
        LocalDate monday = WeekUtils.mondayOf(query.getWeekStart());
        LocalDate sunday = monday.plusDays(6);
        List<StatsVO.DailyStat> daily = query.getGoalId() == null
                ? taskMapper.selectDailyStats(userId, monday, sunday)
                : taskMapper.selectDailyStatsByGoal(userId, query.getGoalId(), monday, sunday);

        Map<LocalDate, StatsVO.DailyStat> statMap = new HashMap<>();
        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal finishedWeight = BigDecimal.ZERO;
        long taskTotal = 0L;
        List<BigDecimal> ratesWithTask = new ArrayList<>();
        for (StatsVO.DailyStat stat : daily) {
            statMap.put(stat.getTaskDate(), stat);
            totalWeight = totalWeight.add(nz(stat.getWeightTotal()));
            finishedWeight = finishedWeight.add(nz(stat.getWeightFinished()));
            taskTotal += stat.getTaskTotal() == null ? 0L : stat.getTaskTotal();
        }

        List<ViewDTO.TrendPoint> trend = new ArrayList<>();
        for (LocalDate date : WeekUtils.weekDays(monday)) {
            ViewDTO.TrendPoint point = new ViewDTO.TrendPoint();
            point.setDate(date);
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
            trend.add(point);
        }

        ViewDTO.WeeklyVO vo = new ViewDTO.WeeklyVO();
        vo.setWeekStart(monday);
        vo.setWeekEnd(sunday);
        vo.setCompletionRate(StatsCalc.rate(finishedWeight, totalWeight));
        vo.setAvgDailyRate(StatsCalc.avg(ratesWithTask));
        vo.setTaskTotal(taskTotal);
        vo.setDailyTrend(trend);
        vo.setSubjectRates(subjectRates(userId, monday, sunday, query.getGoalId()));
        return vo;
    }

    private List<ViewDTO.SubjectRate> subjectRates(Long userId, LocalDate start,
                                                   LocalDate end, Long goalId) {
        List<StudySubject> subjects = subjectMapper.selectList(Wrappers.<StudySubject>lambdaQuery()
                .eq(StudySubject::getUserId, userId)
                .eq(goalId != null, StudySubject::getGoalId, goalId));
        Map<Long, StatsVO.SubjectStat> statMap = new HashMap<>();
        for (StatsVO.SubjectStat stat : taskMapper.selectSubjectStats(userId, start, end)) {
            statMap.put(stat.getSubjectId(), stat);
        }
        List<ViewDTO.SubjectRate> rates = new ArrayList<>();
        for (StudySubject subject : subjects) {
            ViewDTO.SubjectRate item = new ViewDTO.SubjectRate();
            item.setSubjectId(subject.getId());
            item.setName(subject.getName());
            StatsVO.SubjectStat stat = statMap.get(subject.getId());
            item.setCompletionRate(stat == null ? StatsCalc.rate(null, null)
                    : StatsCalc.rate(stat.getWeightFinished(), stat.getWeightTotal()));
            rates.add(item);
        }
        return rates;
    }

    // ---------------- CSV 导出 ----------------

    /** 流式输出 UTF-8 BOM 的 CSV，浏览器直接下载 */
    public void exportCsv(Long userId, ViewDTO.WeekQuery query, HttpServletResponse response) throws IOException {
        LocalDate monday = WeekUtils.mondayOf(query.getWeekStart());
        LocalDate sunday = monday.plusDays(6);
        List<DailyTask> tasks = taskService.listForWeek(userId, monday, sunday, query.getGoalId());

        Map<Long, Long> imageCounts = new HashMap<>();
        for (StatsVO.ImageCount count : imageMapper.countByWeek(userId, monday, sunday)) {
            imageCounts.put(count.getTaskId(), count.getImageCount());
        }
        Map<Long, String> goalNames = new HashMap<>();
        for (StudyGoal goal : goalMapper.selectList(Wrappers.<StudyGoal>lambdaQuery()
                .eq(StudyGoal::getUserId, userId))) {
            goalNames.put(goal.getId(), goal.getName());
        }
        Map<Long, String> subjectNames = new HashMap<>();
        for (StudySubject subject : subjectMapper.selectList(Wrappers.<StudySubject>lambdaQuery()
                .eq(StudySubject::getUserId, userId))) {
            subjectNames.put(subject.getId(), subject.getName());
        }

        // 文件名只含校验后的 ISO 日期，不含任何用户输入，防响应头注入
        String filename = "weekly_" + monday + "_" + sunday + ".csv";
        response.setContentType("text/csv; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");

        PrintWriter writer = new PrintWriter(new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8));
        // UTF-8 BOM：Excel 打开中文不乱码
        writer.print('\uFEFF');
        writer.println(String.join(",",
                "周起始", "周结束", "备考目标", "科目", "任务日期", "任务内容", "权重",
                "掌握度", "截止时间", "完成状态", "完成时间", "复盘备注", "图片标记", "图片数量"));

        for (DailyTask task : tasks) {
            long count = imageCounts.getOrDefault(task.getId(), 0L);
            List<String> row = List.of(
                    monday.toString(), sunday.toString(),
                    goalNames.getOrDefault(task.getGoalId(), "已删除目标"),
                    subjectNames.getOrDefault(task.getSubjectId(), "已删除科目"),
                    str(task.getTaskDate()), task.getTitle(),
                    str(task.getWeight()), str(task.getMastery()),
                    str(task.getDeadline()),
                    task.getFinishStatus() != null && task.getFinishStatus() == 1 ? "已完成" : "未完成",
                    str(task.getFinishedAt()),
                    task.getReviewNote() == null ? "" : task.getReviewNote(),
                    // 仅保留图片标记，不嵌入图片、不输出短时效临时链接
                    count > 0 ? "[IMAGE]" : "",
                    String.valueOf(count));
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < row.size(); i++) {
                if (i > 0) {
                    line.append(',');
                }
                line.append(csvCell(row.get(i)));
            }
            writer.println(line);
        }
        writer.flush();
        log.info("周报 CSV 导出完成 userId={} week={} rows={}", userId, monday, tasks.size());
    }

    /**
     * CSV 单元格转义：双引号包裹 + 内部引号翻倍；
     * 以 = + - @ 等开头的单元格前置单引号，防 CSV 公式注入。
     */
    private String csvCell(String value) {
        String text = value == null ? "" : value;
        if (!text.isEmpty()) {
            char first = text.charAt(0);
            if (first == '=' || first == '+' || first == '-' || first == '@'
                    || first == '\t' || first == '\r' || first == '\n') {
                text = "'" + text;
            }
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    // ---------------- 历史归档 ----------------

    public Result.Page<ViewDTO.WeekVO> archiveWeeks(Long userId, long page, long size) {
        IPage<WeeklySummary> result = summaryMapper.selectPage(new Page<>(page, size),
                Wrappers.<WeeklySummary>lambdaQuery()
                        .eq(WeeklySummary::getUserId, userId)
                        .eq(WeeklySummary::getGoalId, 0L)
                        .orderByDesc(WeeklySummary::getWeekStart));
        List<ViewDTO.WeekVO> list = new ArrayList<>();
        for (WeeklySummary summary : result.getRecords()) {
            list.add(toWeekVO(summary));
        }
        return new Result.Page<>(list, result.getTotal(), page, size);
    }

    public ViewDTO.WeekDetailVO archiveWeekDetail(Long userId, LocalDate weekStart) {
        LocalDate monday = WeekUtils.mondayOf(weekStart);
        WeeklySummary summary = summaryMapper.selectOne(Wrappers.<WeeklySummary>lambdaQuery()
                .eq(WeeklySummary::getUserId, userId)
                .eq(WeeklySummary::getGoalId, 0L)
                .eq(WeeklySummary::getWeekStart, monday)
                .last("LIMIT 1"));
        ViewDTO.WeekDetailVO vo = new ViewDTO.WeekDetailVO();
        vo.setSummary(summary == null ? null : toWeekVO(summary));
        List<BizDTO.TaskVO> tasks = taskService.toVOList(userId,
                taskService.listForWeek(userId, monday, monday.plusDays(6), null));
        vo.setTasks(tasks);
        return vo;
    }

    public Result.Page<BizDTO.TaskVO> archiveTasks(Long userId, ViewDTO.ArchiveTaskQuery query) {
        return taskService.archivePage(userId, query);
    }

    private ViewDTO.WeekVO toWeekVO(WeeklySummary s) {
        ViewDTO.WeekVO vo = new ViewDTO.WeekVO();
        vo.setId(s.getId());
        vo.setWeekStart(s.getWeekStart());
        vo.setWeekEnd(s.getWeekEnd());
        vo.setCompletionRate(s.getCompletionRate());
        vo.setAvgDailyRate(s.getAvgDailyRate());
        vo.setCheckinDays(s.getCheckinDays());
        vo.setTaskTotal(s.getTaskTotal());
        vo.setTaskFinished(s.getTaskFinished());
        vo.setSnapshotAt(s.getSnapshotAt());
        return vo;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
