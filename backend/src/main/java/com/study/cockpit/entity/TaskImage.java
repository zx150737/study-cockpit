package com.study.cockpit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务图片存储记录（15 天有效）。
 * relative_path 由后端生成（年/月/UUID.ext），绝不采用用户原始文件名。
 * 到期时由 ImageExpireCleanTask 物理删除本记录与磁盘文件。
 */
@Data
@TableName("task_image")
public class TaskImage {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long taskId;
    private String originalName;
    private String storedName;
    private String relativePath;
    private String ext;
    private String mimeType;
    private Long fileSize;
    private Integer magicVerified;
    /** 上传时间：15 天倒计时起点 */
    private LocalDateTime uploadTime;
    /** 过期时间 = uploadTime + 15 天 */
    private LocalDateTime expireTime;
    private LocalDateTime createdAt;
}
