package com.study.cockpit.controller;

import com.study.cockpit.common.Result;
import com.study.cockpit.dto.ViewDTO;
import com.study.cockpit.security.SecurityUtils;
import com.study.cockpit.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 主驾驶舱首页聚合接口（需求 5） */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/overview")
    public Result<ViewDTO.OverviewVO> overview() {
        return Result.ok(dashboardService.overview(SecurityUtils.getUserId()));
    }
}
