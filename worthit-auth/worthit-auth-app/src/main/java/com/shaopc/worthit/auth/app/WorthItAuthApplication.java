package com.shaopc.worthit.auth.app;

import com.shaopc.worthit.common.data.config.WorthItMybatisPlusConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * WorthIt 认证服务启动入口。
 */
@Import(WorthItMybatisPlusConfiguration.class)
@SpringBootApplication
public class WorthItAuthApplication {

    /**
     * 启动认证服务。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(WorthItAuthApplication.class, args);
    }
}
