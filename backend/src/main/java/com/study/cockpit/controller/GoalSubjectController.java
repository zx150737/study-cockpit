package com.study.cockpit.controller;

import com.study.cockpit.common.Result;
import com.study.cockpit.dto.BizDTO;
import com.study.cockpit.security.SecurityUtils;
import com.study.cockpit.service.GoalSubjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 目标与科目管理接口（需求 1） */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class GoalSubjectController {

    private final GoalSubjectService goalSubjectService;

    @GetMapping("/goals")
    public Result<List<BizDTO.GoalVO>> listGoals() {
        return Result.ok(goalSubjectService.listGoals(SecurityUtils.getUserId()));
    }

    @PostMapping("/goals")
    public Result<BizDTO.GoalVO> createGoal(@Valid @RequestBody BizDTO.GoalReq req) {
        return Result.ok(goalSubjectService.createGoal(SecurityUtils.getUserId(), req));
    }

    @PutMapping("/goals/{id}")
    public Result<Void> updateGoal(@PathVariable Long id, @Valid @RequestBody BizDTO.GoalReq req) {
        goalSubjectService.updateGoal(SecurityUtils.getUserId(), id, req);
        return Result.ok();
    }

    @DeleteMapping("/goals/{id}")
    public Result<Void> deleteGoal(@PathVariable Long id) {
        goalSubjectService.deleteGoal(SecurityUtils.getUserId(), id);
        return Result.ok();
    }

    @GetMapping("/goals/{goalId}/subjects")
    public Result<List<BizDTO.SubjectVO>> listSubjects(@PathVariable Long goalId) {
        return Result.ok(goalSubjectService.listSubjects(SecurityUtils.getUserId(), goalId));
    }

    @PostMapping("/subjects")
    public Result<BizDTO.SubjectVO> createSubject(@Valid @RequestBody BizDTO.SubjectReq req) {
        return Result.ok(goalSubjectService.createSubject(SecurityUtils.getUserId(), req));
    }

    @PutMapping("/subjects/{id}")
    public Result<Void> updateSubject(@PathVariable Long id, @Valid @RequestBody BizDTO.SubjectReq req) {
        goalSubjectService.updateSubject(SecurityUtils.getUserId(), id, req);
        return Result.ok();
    }

    @DeleteMapping("/subjects/{id}")
    public Result<Void> deleteSubject(@PathVariable Long id) {
        goalSubjectService.deleteSubject(SecurityUtils.getUserId(), id);
        return Result.ok();
    }
}
