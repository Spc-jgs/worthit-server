package com.shaopc.worthit.reminder.app;

import com.shaopc.worthit.common.data.config.WorthItMybatisPlusConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * WorthIt 提醒服务启动入口。
 */
@Import(WorthItMybatisPlusConfiguration.class)
@SpringBootApplication
public class WorthItReminderApplication {

    /**
     * 启动提醒服务。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(WorthItReminderApplication.class, args);
    }
}
