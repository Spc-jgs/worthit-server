package com.shaopc.worthit.reminder.app.infra;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 仅在本地基础设施联调时提供 Reminder 注册发现探针。
 */
@Profile("local-infra")
@RestController
@RequestMapping("/internal/__infra")
public final class LocalInfraProbeController {

    /**
     * 返回不包含配置和运行时细节的就绪标识。
     *
     * @return Reminder 探针响应
     */
    @GetMapping("/ping")
    public ProbeResponse ping() {
        return new ProbeResponse("worthit-reminder", "ready");
    }

    /**
     * 本地注册发现探针响应。
     *
     * @param service 服务名
     * @param probe   稳定探针状态
     */
    public record ProbeResponse(String service, String probe) {
    }
}
