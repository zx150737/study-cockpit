package com.study.cockpit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.study.cockpit.dto.StatsVO;
import com.study.cockpit.entity.TaskImage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TaskImageMapper extends BaseMapper<TaskImage> {

    /** 过期清理：命中 idx_expire 索引，分页批量扫描，避免长事务 */
    @Select("""
            SELECT * FROM task_image
            WHERE expire_time <= #{now}
            ORDER BY expire_time ASC
            LIMIT #{limit}
            """)
    List<TaskImage> selectExpiredBatch(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /** CSV 导出：按周统计每个任务的图片数量，仅输出标记不输出文件/URL */
    @Select("""
            SELECT ti.task_id AS taskId, COUNT(*) AS imageCount
            FROM task_image ti
            INNER JOIN daily_task dt ON dt.id = ti.task_id
            WHERE ti.user_id = #{userId} AND dt.user_id = #{userId} AND dt.deleted = 0
              AND dt.task_date BETWEEN #{start} AND #{end}
            GROUP BY ti.task_id
            """)
    List<StatsVO.ImageCount> countByWeek(@Param("userId") Long userId,
                                         @Param("start") LocalDate start,
                                         @Param("end") LocalDate end);
}
