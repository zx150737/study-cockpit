package com.study.cockpit.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.study.cockpit.common.BusinessException;
import com.study.cockpit.common.Result;
import com.study.cockpit.dto.BizDTO;
import com.study.cockpit.dto.ViewDTO;
import com.study.cockpit.entity.DailyTask;
import com.study.cockpit.entity.StudyGoal;
import com.study.cockpit.entity.StudySubject;
import com.study.cockpit.entity.TaskImage;
import com.study.cockpit.mapper.DailyTaskMapper;
import com.study.cockpit.mapper.StudyGoalMapper;
import com.study.cockpit.mapper.StudySubjectMapper;
import com.study.cockpit.mapper.TaskImageMapper;
import com.study.cockpit.util.WeekUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 每日任务服务（需求 2）。
 * 数据隔离：所有查询/更新/删除强制带 user_id；跨用户资源一律 404。
 * 积压判定：未完成 且（归属日已过 或 截止时间已过），命中项前端标红。
 */
@Service
@RequiredArgsConstructor
public class TaskService {

    private final DailyTaskMapper taskMapper;
    private final StudyGoalMapper goalMapper;
    private final StudySubjectMapper subjectMapper;
    private final TaskImageMapper imageMapper;
    private final GoalSubjectService goalSubjectService;

    public Result.Page<BizDTO.TaskVO> page(Long userId, BizDTO.TaskQuery query) {
        var wrapper = Wrappers.<DailyTask>lambdaQuery()
                .eq(DailyTask::getUserId, userId);
        if (query.getTaskDate() != null) {
            wrapper.eq(DailyTask::getTaskDate, query.getTaskDate());
        }
        if (query.getWeekStart() != null) {
            LocalDate monday = WeekUtils.mondayOf(query.getWeekStart());
            wrapper.between(DailyTask::getTaskDate, monday, monday.plusDays(6));
        }
        if (query.getGoalId() != null) {
            wrapper.eq(DailyTask::getGoalId, query.getGoalId());
        }
        if (query.getSubjectId() != null) {
            wrapper.eq(DailyTask::getSubjectId, query.getSubjectId());
        }
        if (query.getFinishStatus() != null) {
            wrapper.eq(DailyTask::getFinishStatus, query.getFinishStatus());
        }
        if (Boolean.TRUE.equals(query.getOverdue())) {
            // {0}/{1} 为参数占位，值全部走预编译绑定，不拼接任何用户输入
            wrapper.eq(DailyTask::getFinishStatus, 0)
                    .apply("(task_date < {0} OR (deadline IS NOT NULL AND deadline < {1}))",
                            WeekUtils.today(), WeekUtils.now());
        }
        wrapper.orderByDesc(DailyTask::getTaskDate).orderByDesc(DailyTask::getId);

        IPage<DailyTask> page = taskMapper.selectPage(new Page<>(query.getPage(), query.getSize()), wrapper);
        List<BizDTO.TaskVO> list = toVOList(userId, page.getRecords());
        return new Result.Page<>(list, page.getTotal(), query.getPage(), query.getSize());
    }

    public BizDTO.TaskVO create(Long userId, BizDTO.TaskReq req) {
        StudySubject subject = goalSubjectService.requireSubject(userId, req.getSubjectId());
        if (!subject.getGoalId().equals(req.getGoalId())) {
            throw BusinessException.param("科目不属于所选备考目标");
        }
        goalSubjectService.requireGoal(userId, req.getGoalId());

        DailyTask task = new DailyTask();
        task.setUserId(userId);
        task.setGoalId(req.getGoalId());
        task.setSubjectId(req.getSubjectId());
        task.setTitle(req.getTitle().trim());
        // 未显式指定权重时，取科目的默认权重
        task.setWeight(req.getWeight() != null ? req.getWeight() : subject.getDefaultWeight());
        task.setTaskDate(req.getTaskDate());
        task.setDeadline(req.getDeadline());
        task.setMastery(req.getMastery());
        task.setReviewNote(req.getReviewNote());
        task.setFinishStatus(0);
        task.setDeleted(0);
        taskMapper.insert(task);
        return toVOList(userId, List.of(task)).get(0);
    }

    public void update(Long userId, Long taskId, BizDTO.TaskReq req) {
        DailyTask task = requireTask(userId, taskId);
        StudySubject subject = goalSubjectService.requireSubject(userId, req.getSubjectId());
        if (!subject.getGoalId().equals(req.getGoalId())) {
            throw BusinessException.param("科目不属于所选备考目标");
        }
        goalSubjectService.requireGoal(userId, req.getGoalId());

        task.setGoalId(req.getGoalId());
        task.setSubjectId(req.getSubjectId());
        task.setTitle(req.getTitle().trim());
        task.setWeight(req.getWeight() != null ? req.getWeight() : subject.getDefaultWeight());
        task.setTaskDate(req.getTaskDate());
        task.setDeadline(req.getDeadline());
        task.setMastery(req.getMastery());
        task.setReviewNote(req.getReviewNote());
        taskMapper.updateById(task);
    }

    public void delete(Long userId, Long taskId) {
        requireTask(userId, taskId);
        taskMapper.deleteById(taskId);
    }

    public void finish(Long userId, Long taskId, Integer finishStatus) {
        DailyTask task = requireTask(userId, taskId);
        task.setFinishStatus(finishStatus);
        task.setFinishedAt(finishStatus == 1 ? WeekUtils.now() : null);
        taskMapper.updateById(task);
    }

    /** 指定周内的任务明细（CSV 导出 / 归档详情复用），过滤条件下推到 SQL */
    public List<DailyTask> listForWeek(Long userId, LocalDate start, LocalDate end, Long goalId) {
        var wrapper = Wrappers.<DailyTask>lambdaQuery()
                .eq(DailyTask::getUserId, userId)
                .between(DailyTask::getTaskDate, start, end);
        if (goalId != null) {
            wrapper.eq(DailyTask::getGoalId, goalId);
        }
        wrapper.orderByAsc(DailyTask::getTaskDate).orderByAsc(DailyTask::getId);
        return taskMapper.selectList(wrapper);
    }

    /** 归档任务分页 */
    public Result.Page<BizDTO.TaskVO> archivePage(Long userId, ViewDTO.ArchiveTaskQuery query) {
        BizDTO.TaskQuery tq = new BizDTO.TaskQuery();
        tq.setWeekStart(query.getWeekStart());
        tq.setGoalId(query.getGoalId());
        tq.setFinishStatus(query.getFinishStatus());
        tq.setPage(query.getPage());
        tq.setSize(query.getSize());
        return page(userId, tq);
    }

    public long countBacklog(Long userId) {
        Long count = taskMapper.selectCount(Wrappers.<DailyTask>lambdaQuery()
                .eq(DailyTask::getUserId, userId)
                .eq(DailyTask::getFinishStatus, 0)
                .apply("(task_date < {0} OR (deadline IS NOT NULL AND deadline < {1}))",
                        WeekUtils.today(), WeekUtils.now()));
        return count == null ? 0L : count;
    }

    /** 积压预警列表（登录提醒 / 首页预警面板） */
    public ViewDTO.OverdueVO overdue(Long userId, int limit) {
        List<DailyTask> tasks = taskMapper.selectBacklog(userId, WeekUtils.today(), WeekUtils.now(), limit);
        Map<Long, String> goalNames = loadGoalNames(userId);
        Map<Long, String> subjectNames = loadSubjectNames(userId);
        ViewDTO.OverdueVO vo = new ViewDTO.OverdueVO();
        vo.setTotal(countBacklog(userId));
        List<ViewDTO.BacklogItem> items = new ArrayList<>();
        for (DailyTask task : tasks) {
            items.add(toBacklogItem(task, goalNames, subjectNames));
        }
        vo.setItems(items);
        return vo;
    }

    public DailyTask requireTask(Long userId, Long taskId) {
        DailyTask task = taskMapper.selectOne(Wrappers.<DailyTask>lambdaQuery()
                .eq(DailyTask::getId, taskId)
                .eq(DailyTask::getUserId, userId)
                .last("LIMIT 1"));
        if (task == null) {
            throw BusinessException.notFound();
        }
        return task;
    }

    // ---------------- 转换与辅助 ----------------

    public List<BizDTO.TaskVO> toVOList(Long userId, List<DailyTask> tasks) {
        if (tasks.isEmpty()) {
            return List.of();
        }
        Map<Long, String> goalNames = loadGoalNames(userId);
        Map<Long, String> subjectNames = loadSubjectNames(userId);

        List<Long> taskIds = tasks.stream().map(DailyTask::getId).toList();
        Map<Long, Integer> imageCounts = new HashMap<>();
        // IN + user_id 已下推到 SQL，仅对本页任务的图片做轻量分组
        List<TaskImage> images = imageMapper.selectList(Wrappers.<TaskImage>lambdaQuery()
                .eq(TaskImage::getUserId, userId)
                .in(TaskImage::getTaskId, taskIds));
        for (TaskImage image : images) {
            imageCounts.merge(image.getTaskId(), 1, Integer::sum);
        }

        LocalDate today = WeekUtils.today();
        LocalDateTime now = WeekUtils.now();
        List<BizDTO.TaskVO> result = new ArrayList<>();
        for (DailyTask task : tasks) {
            BizDTO.TaskVO vo = new BizDTO.TaskVO();
            vo.setId(task.getId());
            vo.setGoalId(task.getGoalId());
            vo.setGoalName(goalNames.getOrDefault(task.getGoalId(), "已删除目标"));
            vo.setSubjectId(task.getSubjectId());
            vo.setSubjectName(subjectNames.getOrDefault(task.getSubjectId(), "已删除科目"));
            vo.setTitle(task.getTitle());
            vo.setWeight(task.getWeight());
            vo.setTaskDate(task.getTaskDate());
            vo.setDeadline(task.getDeadline());
            vo.setMastery(task.getMastery());
            vo.setReviewNote(task.getReviewNote());
            vo.setFinishStatus(task.getFinishStatus());
            vo.setFinishedAt(task.getFinishedAt());
            vo.setCreatedAt(task.getCreatedAt());
            vo.setOverdue(isOverdue(task, today, now));
            vo.setImageCount(imageCounts.getOrDefault(task.getId(), 0));
            result.add(vo);
        }
        return result;
    }

    public boolean isOverdue(DailyTask task, LocalDate today, LocalDateTime now) {
        if (task.getFinishStatus() != null && task.getFinishStatus() == 1) {
            return false;
        }
        boolean datePassed = task.getTaskDate() != null && task.getTaskDate().isBefore(today);
        boolean deadlinePassed = task.getDeadline() != null && task.getDeadline().isBefore(now);
        return datePassed || deadlinePassed;
    }

    public ViewDTO.BacklogItem toBacklogItem(DailyTask task,
                                             Map<Long, String> goalNames,
                                             Map<Long, String> subjectNames) {
        ViewDTO.BacklogItem item = new ViewDTO.BacklogItem();
        item.setTaskId(task.getId());
        item.setTitle(task.getTitle());
        item.setGoalName(goalNames.getOrDefault(task.getGoalId(), "已删除目标"));
        item.setSubjectName(subjectNames.getOrDefault(task.getSubjectId(), "已删除科目"));
        item.setTaskDate(task.getTaskDate());
        item.setDeadline(task.getDeadline());
        long days = task.getTaskDate() == null ? 0L
                : Math.max(0L, ChronoUnit.DAYS.between(task.getTaskDate(), WeekUtils.today()));
        item.setOverdueDays(days);
        return item;
    }

    private Map<Long, String> loadGoalNames(Long userId) {
        Map<Long, String> map = new HashMap<>();
        for (StudyGoal goal : goalMapper.selectList(Wrappers.<StudyGoal>lambdaQuery()
                .eq(StudyGoal::getUserId, userId))) {
            map.put(goal.getId(), goal.getName());
        }
        return map;
    }

    private Map<Long, String> loadSubjectNames(Long userId) {
        Map<Long, String> map = new HashMap<>();
        for (StudySubject subject : subjectMapper.selectList(Wrappers.<StudySubject>lambdaQuery()
                .eq(StudySubject::getUserId, userId))) {
            map.put(subject.getId(), subject.getName());
        }
        return map;
    }
}
