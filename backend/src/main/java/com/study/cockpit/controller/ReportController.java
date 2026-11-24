package com.study.cockpit.controller;

import com.study.cockpit.common.BusinessException;
import com.study.cockpit.common.Result;
import com.study.cockpit.dto.BizDTO;
import com.study.cockpit.dto.ViewDTO;
import com.study.cockpit.security.SecurityUtils;
import com.study.cockpit.service.NotificationService;
import com.study.cockpit.service.ReportService;
import com.study.cockpit.util.WeekUtils;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;

/** 周度分析 / CSV 导出 / 历史归档 / 积压预警接口 */
@RestController
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final NotificationService notificationService;

    @GetMapping("/api/reports/weekly")
    public Result<ViewDTO.WeeklyVO> weekly(@Valid @ModelAttribute ViewDTO.WeekQuery query) {
        return Result.ok(reportService.weekly(SecurityUtils.getUserId(), query));
    }

    /** 浏览器直接下载 CSV；不打包图片，仅保留图片标记 */
    @GetMapping("/api/reports/weekly/export")
    public void export(@Valid @ModelAttribute ViewDTO.WeekQuery query,
                       HttpServletResponse response) throws IOException {
        reportService.exportCsv(SecurityUtils.getUserId(), query, response);
    }

    @GetMapping("/api/archive/weeks")
    public Result<Result.Page<ViewDTO.WeekVO>> archiveWeeks(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return Result.ok(reportService.archiveWeeks(SecurityUtils.getUserId(), page, size));
    }

    @GetMapping("/api/archive/weeks/{weekStart}")
    public Result<ViewDTO.WeekDetailVO> archiveWeekDetail(@PathVariable String weekStart) {
        return Result.ok(reportService.archiveWeekDetail(SecurityUtils.getUserId(), parseWeek(weekStart)));
    }

    @GetMapping("/api/archive/tasks")
    public Result<Result.Page<BizDTO.TaskVO>> archiveTasks(
            @Valid @ModelAttribute ViewDTO.ArchiveTaskQuery query) {
        return Result.ok(reportService.archiveTasks(SecurityUtils.getUserId(), query));
    }

    /** 登录后展示的积压预警 */
    @GetMapping("/api/notifications/overdue")
    public Result<ViewDTO.OverdueVO> overdue() {
        return Result.ok(notificationService.overdue(SecurityUtils.getUserId()));
    }

    /** 严格解析路径中的周一日期，非法输入统一转 400 */
    private LocalDate parseWeek(String text) {
        try {
            return WeekUtils.parseWeekStart(text);
        } catch (IllegalArgumentException e) {
            throw BusinessException.param(e.getMessage());
        }
    }
}
