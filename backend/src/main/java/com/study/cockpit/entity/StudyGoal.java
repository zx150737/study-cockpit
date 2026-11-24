package com.study.cockpit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 备考目标表（考研 / 教师编 / 自定义），逻辑删除以保留历史归档 */
@Data
@TableName("study_goal")
public class StudyGoal {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String name;
    @TableLogic(value = "0", delval = "1")
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
