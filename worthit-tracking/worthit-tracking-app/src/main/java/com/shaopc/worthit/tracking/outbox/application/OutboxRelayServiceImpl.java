package com.shaopc.worthit.tracking.outbox.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shaopc.worthit.reminder.client.api.ReminderCommandClient;
import com.shaopc.worthit.reminder.client.command.ReconcileReminderCommand;
import com.shaopc.worthit.reminder.client.contract.ReminderClientContract;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * 抢占并投递 Tracking Outbox 事件。
 */
@Service
public class OutboxRelayServiceImpl implements OutboxRelayService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(OutboxRelayServiceImpl.class);
    private static final int MAX_ERROR_LENGTH = 512;

    private final OutboxRelayRepository repository;
    private final ReminderCommandClient reminderClient;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final OutboxRelayProperties properties;
    private final Clock trackingClock;
    private final String ownerId;

    /**
     * 创建单进程唯一的 Relay 租约持有者。
     */
    public OutboxRelayServiceImpl(
            OutboxRelayRepository repository,
            ReminderCommandClient reminderClient,
            ObjectMapper objectMapper,
            Validator validator,
            OutboxRelayProperties properties,
            Clock trackingClock) {
        this.repository = repository;
        this.reminderClient = reminderClient;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.properties = properties;
        this.trackingClock = trackingClock;
        this.ownerId =
                "tracking-outbox-" + UUID.randomUUID();
    }

    /**
     * 抢占并逐条投递一批事件。
     *
     * @return 本轮抢占事件数
     */
    @Override
    public int relayBatch() {
        LocalDateTime now = LocalDateTime.now(trackingClock);
        var events = repository.claim(
                ownerId,
                now,
                now.minus(properties.leaseDuration()),
                properties.batchSize());
        for (ClaimedOutboxEvent event : events) {
            deliver(event);
        }
        return events.size();
    }

    private void deliver(ClaimedOutboxEvent event) {
        try {
            ReconcileReminderCommand command = deserialize(event);
            reminderClient.reconcile(event.eventId(), command);
            markSucceeded(event);
        } catch (Exception exception) {
            markFailed(event, exception);
        }
    }

    private ReconcileReminderCommand deserialize(
            ClaimedOutboxEvent event) throws JsonProcessingException {
        if (OutboxEventType.fromCode(event.eventType())
                != OutboxEventType.REMINDER_RECONCILE) {
            throw new IllegalArgumentException(
                    "不支持的Outbox事件类型: "
                            + event.eventType());
        }
        if (event.schemaVersion()
                != ReminderClientContract.SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "不支持的Outbox契约版本: "
                            + event.schemaVersion());
        }
        ReconcileReminderCommand command = objectMapper.readValue(
                event.payloadJson(),
                ReconcileReminderCommand.class);
        Set<ConstraintViolation<ReconcileReminderCommand>> violations =
                validator.validate(command);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(
                    "Outbox载荷不符合Reminder契约");
        }
        return command;
    }

    private void markSucceeded(ClaimedOutboxEvent event) {
        LocalDateTime now = LocalDateTime.now(trackingClock);
        if (!repository.markSucceeded(
                event.id(), ownerId, now)) {
            LOGGER.warn(
                    "Outbox成功回写丢失租约 eventId={}",
                    event.eventId());
        }
    }

    private void markFailed(
            ClaimedOutboxEvent event,
            Exception exception) {
        LocalDateTime now = LocalDateTime.now(trackingClock);
        int retryCount = event.retryCount() + 1;
        boolean dead = retryCount >= properties.maxRetries();
        OutboxStatus status = dead
                ? OutboxStatus.DEAD
                : OutboxStatus.RETRY_WAIT;
        LocalDateTime nextRetryAt = dead
                ? null
                : now.plus(backoff(retryCount));
        LocalDateTime processedAt = dead ? now : null;
        boolean updated = repository.markFailed(
                event.id(),
                ownerId,
                status,
                retryCount,
                nextRetryAt,
                errorSummary(exception),
                processedAt,
                now);
        if (!updated) {
            LOGGER.warn(
                    "Outbox失败回写丢失租约 eventId={}",
                    event.eventId());
            return;
        }
        if (dead) {
            LOGGER.error(
                    "Outbox事件进入DEAD eventId={}, retries={}",
                    event.eventId(),
                    retryCount,
                    exception);
        } else {
            LOGGER.warn(
                    "Outbox投递失败等待重试 eventId={}, retries={}",
                    event.eventId(),
                    retryCount,
                    exception);
        }
    }

    private Duration backoff(int retryCount) {
        long multiplier = 1L << Math.min(
                retryCount - 1, properties.maxRetries() - 1);
        Duration candidate;
        try {
            candidate = properties.initialBackoff()
                    .multipliedBy(multiplier);
        } catch (ArithmeticException exception) {
            return properties.maxBackoff();
        }
        return candidate.compareTo(properties.maxBackoff()) > 0
                ? properties.maxBackoff()
                : candidate;
    }

    private static String errorSummary(Exception exception) {
        String message = exception.getMessage();
        String summary = exception.getClass().getSimpleName()
                + (message == null || message.isBlank()
                ? ""
                : ": " + message);
        summary = summary
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replace('\t', ' ');
        return summary.length() <= MAX_ERROR_LENGTH
                ? summary
                : summary.substring(0, MAX_ERROR_LENGTH);
    }
}
