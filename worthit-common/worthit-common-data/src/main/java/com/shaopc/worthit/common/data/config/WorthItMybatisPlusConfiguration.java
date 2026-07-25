package com.shaopc.worthit.common.data.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 提供各业务服务统一使用的 MyBatis-Plus 基础插件配置。
 */
@Configuration(proxyBeanMethods = false)
public class WorthItMybatisPlusConfiguration {

    /**
     * 注册 MySQL 分页和乐观锁插件。
     *
     * <p>分页插件必须先于乐观锁插件执行，避免分页 SQL 改写受到后续插件影响。</p>
     *
     * @return 统一的 MyBatis-Plus 插件链
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }
}
