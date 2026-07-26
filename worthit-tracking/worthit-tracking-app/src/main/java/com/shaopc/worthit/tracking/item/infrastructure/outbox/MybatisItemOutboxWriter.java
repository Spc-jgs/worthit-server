package com.shaopc.worthit.tracking.item.infrastructure.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shaopc.worthit.reminder.client.command.ReconcileReminderCommand;
import com.shaopc.worthit.tracking.item.application.ItemOutboxWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 把 Item 保修提醒完整期望写入本地 Outbox。
 */
@Repository
@RequiredArgsConstructor
public class MybatisItemOutboxWriter
        implements ItemOutboxWriter {

    private static final String EVENT_TYPE =
            "REMINDER_RECONCILE";
    private final OutboxEventMapper mapper;
    private final ObjectMapper objectMapper;
    private final Clock trackingClock;

    @Override
    public void write(ReconcileReminderCommand command) {
        try {
            LocalDateTime now =
                    LocalDateTime.now(trackingClock);
            OutboxEventDO event = new OutboxEventDO();
            event.setEventId(UUID.randomUUID().toString());
            event.setAggregateType(
                    command.businessType().name());
            event.setAggregateId(command.businessId());
            event.setUserId(command.userId());
            event.setSourceVersion(command.sourceVersion());
            event.setEventType(EVENT_TYPE);
            event.setPayloadJson(
                    objectMapper.writeValueAsString(command));
            event.setSchemaVersion(command.schemaVersion());
            event.setStatus("NEW");
            event.setRetryCount(0);
            event.setCreateTime(now);
            event.setUpdateTime(now);
            mapper.insert(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Item提醒Outbox序列化失败", exception);
        }
    }
}
