package com.study.cockpit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 科目表：default_weight 作为创建任务时的默认权重 */
@Data
@TableName("study_subject")
public class StudySubject {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long goalId;
    private String name;
    private BigDecimal defaultWeight;
    @TableLogic(value = "0", delval = "1")
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
