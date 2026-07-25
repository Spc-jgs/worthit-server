package com.shaopc.worthit.common.data.autoconfigure;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 自动装配各业务服务统一使用的 MyBatis-Plus 基础插件。
 */
@AutoConfiguration(before = MybatisPlusAutoConfiguration.class)
@ConditionalOnClass(MybatisPlusInterceptor.class)
public class WorthItDataAutoConfiguration {

    /**
     * 注册乐观锁和 MySQL 分页插件。
     *
     * <p>分页插件放在插件链末尾，避免其他插件处理分页改写后的 SQL。</p>
     *
     * @return 统一的 MyBatis-Plus 插件链
     */
    @Bean
    @ConditionalOnMissingBean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
