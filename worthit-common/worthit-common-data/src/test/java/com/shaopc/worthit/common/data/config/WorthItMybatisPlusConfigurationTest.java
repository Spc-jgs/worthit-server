package com.shaopc.worthit.common.data.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorthItMybatisPlusConfigurationTest {

    @Test
    void registersMysqlPaginationBeforeOptimisticLocking() {
        MybatisPlusInterceptor interceptor =
                new WorthItMybatisPlusConfiguration().mybatisPlusInterceptor();

        assertThat(interceptor.getInterceptors())
                .hasExactlyElementsOfTypes(
                        PaginationInnerInterceptor.class,
                        OptimisticLockerInnerInterceptor.class);
        assertThat((PaginationInnerInterceptor) interceptor.getInterceptors().get(0))
                .extracting(PaginationInnerInterceptor::getDbType)
                .isEqualTo(DbType.MYSQL);
    }
}
