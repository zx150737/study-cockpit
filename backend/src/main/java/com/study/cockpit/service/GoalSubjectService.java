package com.study.cockpit.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.study.cockpit.common.BusinessException;
import com.study.cockpit.dto.BizDTO;
import com.study.cockpit.dto.StatsVO;
import com.study.cockpit.entity.StudyGoal;
import com.study.cockpit.entity.StudySubject;
import com.study.cockpit.mapper.DailyTaskMapper;
import com.study.cockpit.mapper.StudyGoalMapper;
import com.study.cockpit.mapper.StudySubjectMapper;
import com.study.cockpit.security.SecurityUtils;
import com.study.cockpit.util.StatsCalc;
import com.study.cockpit.util.WeekUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 目标与科目管理（需求 1）。
 * 所有读写都强制带 user_id；跨用户资源一律返回 404，防止越权与资源枚举。
 * 删除为逻辑删除，保留历史任务数据供归档与周结算使用。
 */
@Service
@RequiredArgsConstructor
public class GoalSubjectService {

    private final StudyGoalMapper goalMapper;
    private final StudySubjectMapper subjectMapper;
    private final DailyTaskMapper dailyTaskMapper;

    // ---------------- 目标 ----------------

    public List<BizDTO.GoalVO> listGoals(Long userId) {
        List<StudyGoal> goals = goalMapper.selectList(Wrappers.<StudyGoal>lambdaQuery()
                .eq(StudyGoal::getUserId, userId)
                .orderByAsc(StudyGoal::getId));

        Map<Long, StatsVO.GoalStat> statsMap = new HashMap<>();
        for (StatsVO.GoalStat stat : dailyTaskMapper.selectGoalStats(userId, StatsCalc.earliest(), WeekUtils.today())) {
            statsMap.put(stat.getGoalId(), stat);
        }

        List<BizDTO.GoalVO> result = new ArrayList<>();
        for (StudyGoal goal : goals) {
            BizDTO.GoalVO vo = new BizDTO.GoalVO();
            vo.setId(goal.getId());
            vo.setName(goal.getName());
            vo.setCreatedAt(goal.getCreatedAt());
            StatsVO.GoalStat stat = statsMap.get(goal.getId());
            vo.setCompletionRate(stat == null
                    ? StatsCalc.rate(null, null)
                    : StatsCalc.rate(stat.getWeightFinished(), stat.getWeightTotal()));
            Long count = subjectMapper.selectCount(Wrappers.<StudySubject>lambdaQuery()
                    .eq(StudySubject::getUserId, userId)
                    .eq(StudySubject::getGoalId, goal.getId()));
            vo.setSubjectCount(count == null ? 0 : count.intValue());
            result.add(vo);
        }
        return result;
    }

    public BizDTO.GoalVO createGoal(Long userId, BizDTO.GoalReq req) {
        StudyGoal goal = new StudyGoal();
        goal.setUserId(userId);
        goal.setName(req.getName().trim());
        goal.setDeleted(0);
        goalMapper.insert(goal);

        BizDTO.GoalVO vo = new BizDTO.GoalVO();
        vo.setId(goal.getId());
        vo.setName(goal.getName());
        vo.setCompletionRate(StatsCalc.rate(null, null));
        vo.setSubjectCount(0);
        return vo;
    }

    public void updateGoal(Long userId, Long goalId, BizDTO.GoalReq req) {
        StudyGoal goal = requireGoal(userId, goalId);
        goal.setName(req.getName().trim());
        goalMapper.updateById(goal);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteGoal(Long userId, Long goalId) {
        // 逻辑删除目标本身；下挂科目与任务保留，保证历史归档数据完整
        requireGoal(userId, goalId);
        goalMapper.deleteById(goalId);
    }

    // ---------------- 科目 ----------------

    public List<BizDTO.SubjectVO> listSubjects(Long userId, Long goalId) {
        requireGoal(userId, goalId);
        List<StudySubject> subjects = subjectMapper.selectList(Wrappers.<StudySubject>lambdaQuery()
                .eq(StudySubject::getUserId, userId)
                .eq(StudySubject::getGoalId, goalId)
                .orderByAsc(StudySubject::getId));

        LocalDate monday = WeekUtils.mondayOf(WeekUtils.today());
        LocalDate sunday = monday.plusDays(6);
        Map<Long, StatsVO.SubjectStat> statsMap = new HashMap<>();
        for (StatsVO.SubjectStat stat : dailyTaskMapper.selectSubjectStats(userId, monday, sunday)) {
            statsMap.put(stat.getSubjectId(), stat);
        }

        List<BizDTO.SubjectVO> result = new ArrayList<>();
        for (StudySubject subject : subjects) {
            BizDTO.SubjectVO vo = new BizDTO.SubjectVO();
            vo.setId(subject.getId());
            vo.setGoalId(subject.getGoalId());
            vo.setName(subject.getName());
            vo.setDefaultWeight(subject.getDefaultWeight());
            StatsVO.SubjectStat stat = statsMap.get(subject.getId());
            vo.setCompletionRate(stat == null
                    ? StatsCalc.rate(null, null)
                    : StatsCalc.rate(stat.getWeightFinished(), stat.getWeightTotal()));
            result.add(vo);
        }
        return result;
    }

    public BizDTO.SubjectVO createSubject(Long userId, BizDTO.SubjectReq req) {
        requireGoal(userId, req.getGoalId());
        StudySubject subject = new StudySubject();
        subject.setUserId(userId);
        subject.setGoalId(req.getGoalId());
        subject.setName(req.getName().trim());
        subject.setDefaultWeight(req.getDefaultWeight() == null ? BigDecimal.ONE : req.getDefaultWeight());
        subject.setDeleted(0);
        subjectMapper.insert(subject);

        BizDTO.SubjectVO vo = new BizDTO.SubjectVO();
        vo.setId(subject.getId());
        vo.setGoalId(subject.getGoalId());
        vo.setName(subject.getName());
        vo.setDefaultWeight(subject.getDefaultWeight());
        vo.setCompletionRate(StatsCalc.rate(null, null));
        return vo;
    }

    public void updateSubject(Long userId, Long subjectId, BizDTO.SubjectReq req) {
        StudySubject subject = requireSubject(userId, subjectId);
        // 允许把科目挪到本人另一个目标下
        requireGoal(userId, req.getGoalId());
        subject.setGoalId(req.getGoalId());
        subject.setName(req.getName().trim());
        subject.setDefaultWeight(req.getDefaultWeight() == null ? BigDecimal.ONE : req.getDefaultWeight());
        subjectMapper.updateById(subject);
    }

    public void deleteSubject(Long userId, Long subjectId) {
        requireSubject(userId, subjectId);
        subjectMapper.deleteById(subjectId);
    }

    // ---------------- 归属校验（防越权统一入口） ----------------

    public StudyGoal requireGoal(Long userId, Long goalId) {
        StudyGoal goal = goalMapper.selectOne(Wrappers.<StudyGoal>lambdaQuery()
                .eq(StudyGoal::getId, goalId)
                .eq(StudyGoal::getUserId, userId)
                .last("LIMIT 1"));
        if (goal == null) {
            throw BusinessException.notFound();
        }
        return goal;
    }

    public StudySubject requireSubject(Long userId, Long subjectId) {
        StudySubject subject = subjectMapper.selectOne(Wrappers.<StudySubject>lambdaQuery()
                .eq(StudySubject::getId, subjectId)
                .eq(StudySubject::getUserId, userId)
                .last("LIMIT 1"));
        if (subject == null) {
            throw BusinessException.notFound();
        }
        return subject;
    }

    /** 供其它服务复用：当前登录用户的目标归属校验 */
    public StudyGoal requireOwnedGoal(Long goalId) {
        return requireGoal(SecurityUtils.getUserId(), goalId);
    }
}
