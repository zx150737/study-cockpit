package com.study.cockpit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.study.cockpit.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("SELECT * FROM sys_user WHERE username = #{username} LIMIT 1")
    SysUser selectByUsername(@Param("username") String username);

    /** 改密/强制下线：令牌版本 +1，使该用户所有旧 JWT 立即失效 */
    @Update("UPDATE sys_user SET token_version = token_version + 1 WHERE id = #{userId}")
    int incrementTokenVersion(@Param("userId") Long userId);
}
