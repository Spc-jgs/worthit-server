package com.shaopc.worthit.reminder.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * WorthIt 提醒服务启动入口。
 */
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
