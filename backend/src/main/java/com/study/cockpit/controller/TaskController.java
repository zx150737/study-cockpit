package com.study.cockpit.controller;

import com.study.cockpit.common.Result;
import com.study.cockpit.dto.BizDTO;
import com.study.cockpit.security.SecurityUtils;
import com.study.cockpit.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 每日任务接口（需求 2）：增删改查、完成标记、分页筛选 */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @GetMapping
    public Result<Result.Page<BizDTO.TaskVO>> page(@Valid @ModelAttribute BizDTO.TaskQuery query) {
        return Result.ok(taskService.page(SecurityUtils.getUserId(), query));
    }

    @PostMapping
    public Result<BizDTO.TaskVO> create(@Valid @RequestBody BizDTO.TaskReq req) {
        return Result.ok(taskService.create(SecurityUtils.getUserId(), req));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody BizDTO.TaskReq req) {
        taskService.update(SecurityUtils.getUserId(), id, req);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        taskService.delete(SecurityUtils.getUserId(), id);
        return Result.ok();
    }

    @PatchMapping("/{id}/finish")
    public Result<Void> finish(@PathVariable Long id, @Valid @RequestBody BizDTO.FinishReq req) {
        taskService.finish(SecurityUtils.getUserId(), id, req.getFinishStatus());
        return Result.ok();
    }
}
