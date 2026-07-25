package com.shaopc.worthit.tracking.infra;

import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * 仅在本地基础设施联调时暴露 Tracking 联合探针。
 */
@Profile("local-infra")
@RestController
@RequestMapping("/internal/__infra")
public final class LocalInfraProbeController {

    private static final String PROBE_MESSAGE_PROPERTY =
            "worthit.runtime.probe-message";

    private final LocalInfraReminderProbeClient reminderProbeClient;
    private final Environment environment;

    /**
     * 创建 Tracking 联合探针。
     *
     * @param reminderProbeClient Reminder 注册发现探针客户端
     * @param environment         可动态刷新的 Spring 环境
     */
    public LocalInfraProbeController(
            LocalInfraReminderProbeClient reminderProbeClient,
            Environment environment) {
        this.reminderProbeClient = Objects.requireNonNull(
                reminderProbeClient, "Reminder探针客户端不能为空");
        this.environment = Objects.requireNonNull(
                environment, "Spring环境不能为空");
    }

    /**
     * 经由服务发现调用 Reminder 探针。
     *
     * @return Reminder 探针响应
     */
    @GetMapping("/reminder/ping")
    public LocalInfraReminderProbeClient.ReminderProbeResponse
            reminderPing() {
        return reminderProbeClient.ping();
    }

    /**
     * 返回唯一允许公开检查的动态配置值。
     *
     * @return 当前探针消息
     */
    @GetMapping("/config")
    public ConfigProbeResponse config() {
        return new ConfigProbeResponse(requireText(
                environment.getProperty(PROBE_MESSAGE_PROPERTY)));
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("运行时探针消息不能为空");
        }
        return value;
    }

    /**
     * 本地配置刷新探针响应。
     *
     * @param probeMessage 当前非敏感探针消息
     */
    public record ConfigProbeResponse(String probeMessage) {
    }
}
