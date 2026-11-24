package com.study.cockpit.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件配置。
 * 安全要求 4/9：分页上限防大分页拖库；BlockAttackInnerInterceptor 拦截无 WHERE 的全表更新/删除。
 * 注意：拦截器不会作用于手写 @Select XML SQL，所有自定义 SQL 必须显式带 user_id 与 deleted=0。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(100L);   // 单页最大 100 条
        pagination.setOverflow(false);  // 页码越界不回绕到首页
        interceptor.addInnerInterceptor(pagination);

        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
        return interceptor;
    }
}
