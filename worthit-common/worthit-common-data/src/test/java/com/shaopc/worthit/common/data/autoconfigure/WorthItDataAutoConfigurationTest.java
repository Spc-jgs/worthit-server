package com.shaopc.worthit.common.data.autoconfigure;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorthItDataAutoConfigurationTest {

    private static final String AUTO_CONFIGURATION_IMPORTS =
            "META-INF/spring/org.springframework.boot.autoconfigure"
                    + ".AutoConfiguration.imports";

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(AutoConfigurationTestApplication.class);

    @Test
    void registersCommonDataThroughBootAutoConfigurationImports()
            throws IOException {
        String configurationClass =
                "com.shaopc.worthit.common.data.autoconfigure"
                        + ".WorthItDataAutoConfiguration";

        assertThat(loadAutoConfigurationImports())
                .anyMatch(content -> content.lines()
                        .anyMatch(configurationClass::equals));
    }

    @Test
    void autoConfiguresMybatisPlusInterceptorFromTheClasspath() {
        contextRunner.run(context ->
                assertThat(context).hasSingleBean(MybatisPlusInterceptor.class));
    }

    @Test
    void registersOptimisticLockingBeforeMysqlPagination() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(MybatisPlusInterceptor.class);
            MybatisPlusInterceptor interceptor =
                    context.getBean(MybatisPlusInterceptor.class);

            assertThat(interceptor.getInterceptors())
                    .hasExactlyElementsOfTypes(
                            OptimisticLockerInnerInterceptor.class,
                            PaginationInnerInterceptor.class);
            assertThat((PaginationInnerInterceptor)
                    interceptor.getInterceptors().get(1))
                    .extracting(PaginationInnerInterceptor::getDbType)
                    .isEqualTo(DbType.MYSQL);
        });
    }

    private List<String> loadAutoConfigurationImports() throws IOException {
        Enumeration<URL> resources = getClass().getClassLoader()
                .getResources(AUTO_CONFIGURATION_IMPORTS);
        List<String> contents = new ArrayList<>();
        while (resources.hasMoreElements()) {
            try (InputStream input = resources.nextElement().openStream()) {
                contents.add(new String(
                        input.readAllBytes(),
                        StandardCharsets.UTF_8));
            }
        }
        return contents;
    }

    @Test
    void backsOffWhenTheApplicationProvidesAnInterceptor() {
        contextRunner
                .withUserConfiguration(InterceptorOverrideConfiguration.class)
                .run(context -> {
                    assertThat(context)
                            .hasSingleBean(MybatisPlusInterceptor.class);
                    assertThat(context.getBean(MybatisPlusInterceptor.class))
                            .isSameAs(context.getBean(
                                    "customMybatisPlusInterceptor",
                                    MybatisPlusInterceptor.class));
                });
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    static class AutoConfigurationTestApplication {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class InterceptorOverrideConfiguration {

        @Bean
        MybatisPlusInterceptor customMybatisPlusInterceptor() {
            return new MybatisPlusInterceptor();
        }
    }
}
