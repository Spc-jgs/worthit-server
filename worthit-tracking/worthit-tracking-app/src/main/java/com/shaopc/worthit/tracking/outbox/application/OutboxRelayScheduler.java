package com.shaopc.worthit.tracking.outbox.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 周期触发 Outbox Relay。
 */
@Component
@ConditionalOnProperty(
        prefix = "worthit.outbox.relay",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class OutboxRelayScheduler {

    private final OutboxRelayService relayService;

    /**
     * 创建 Relay 调度器。
     */
    public OutboxRelayScheduler(
            OutboxRelayService relayService) {
        this.relayService = relayService;
    }

    /**
     * 固定延迟执行，避免同一进程内批次重叠。
     */
    @Scheduled(
            fixedDelayString =
                    "${worthit.outbox.relay.fixed-delay-ms:1000}",
            initialDelayString =
                    "${worthit.outbox.relay.initial-delay-ms:5000}")
    public void relay() {
        relayService.relayBatch();
    }
}
