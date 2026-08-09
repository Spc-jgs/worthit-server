package com.shaopc.worthit.auth.accountcancellation.application;

import com.shaopc.worthit.auth.accountcancellation.application.port.AccountCancellationStore;
import com.shaopc.worthit.auth.accountcancellation.domain.AccountCancellation;
import com.shaopc.worthit.auth.accountcancellation.infrastructure.scheduler.AccountCancellationProperties;
import com.shaopc.worthit.reminder.client.api.ReminderAccountCancellationClient;
import com.shaopc.worthit.reminder.client.command.ReminderAccountCancellationCommand;
import com.shaopc.worthit.reminder.client.response.ReminderAccountCancellationResponse;
import com.shaopc.worthit.tracking.client.api.TrackingAccountCancellationClient;
import com.shaopc.worthit.tracking.client.command.TrackingAccountCancellationCommand;
import com.shaopc.worthit.tracking.client.response.TrackingAccountCancellationResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static com.shaopc.worthit.auth.accountcancellation.domain.AccountCancellationStatus.EXECUTING;
import static com.shaopc.worthit.auth.accountcancellation.domain.AccountCancellationStatus.PENDING;

/** 通过天然幂等全量重试编排 Tracking、Reminder 与 Auth 最终清理。 */
@Service
public class AccountCancellationExecutionServiceImpl
        implements AccountCancellationExecutionService {

    private static final String METRIC = "worthit.auth.account.cancellation";
    private static final Logger LOGGER = LoggerFactory.getLogger(
            AccountCancellationExecutionServiceImpl.class);
    private final AccountCancellationStore store;
    private final AccountCancellationExecutionTransactions transactions;
    private final TrackingAccountCancellationClient trackingClient;
    private final ReminderAccountCancellationClient reminderClient;
    private final AccountCancellationProperties properties;
    private final Clock clock;
    private final Counter success;
    private final Counter claim;
    private final Counter retry;
    private final Counter failure;
    private final Timer trackingDuration;
    private final Timer reminderDuration;
    private final AtomicLong pendingTasks = new AtomicLong();
    private final AtomicLong executingTasks = new AtomicLong();
    private final AtomicLong oldestOpenAgeSeconds = new AtomicLong();

    public AccountCancellationExecutionServiceImpl(
            AccountCancellationStore store,
            AccountCancellationExecutionTransactions transactions,
            TrackingAccountCancellationClient trackingClient,
            ReminderAccountCancellationClient reminderClient,
            AccountCancellationProperties properties,
            Clock clock,
            MeterRegistry meterRegistry) {
        this.store = store;
        this.transactions = transactions;
        this.trackingClient = trackingClient;
        this.reminderClient = reminderClient;
        this.properties = properties;
        this.clock = clock;
        Objects.requireNonNull(meterRegistry, "指标注册器不能为空");
        this.success = counter(meterRegistry, "success");
        this.claim = counter(meterRegistry, "claim");
        this.retry = counter(meterRegistry, "retry");
        this.failure = counter(meterRegistry, "failure");
        this.trackingDuration = downstreamTimer(meterRegistry, "tracking");
        this.reminderDuration = downstreamTimer(meterRegistry, "reminder");
        registerGauge(meterRegistry, "pending", pendingTasks);
        registerGauge(meterRegistry, "executing", executingTasks);
        Gauge.builder(METRIC + ".oldest.open.age.seconds", oldestOpenAgeSeconds, AtomicLong::get)
                .description("最老开放账号注销任务的存续秒数")
                .register(meterRegistry);
    }

    @Override
    public void processBatch() {
        LocalDateTime now = now();
        refreshBacklogMetrics(now);
        for (AccountCancellation candidate
                : store.findExecutable(now, properties.batchSize())) {
            Optional<AccountCancellation> claimed;
            try {
                claimed = transactions.claim(candidate, now);
            } catch (RuntimeException exception) {
                failure.increment();
                LOGGER.warn(
                        "账号注销 claim 失败 cancellationId={}, userId={}, failureType={}",
                        candidate.id(),
                        candidate.userId(),
                        exception.getClass().getSimpleName());
                continue;
            }
            if (claimed.isPresent()) {
                claim.increment();
                try {
                    executeClaimed(claimed.orElseThrow());
                } catch (RuntimeException ignored) {
                    // executeClaimed 已记录脱敏重试指标与日志；继续处理同批其他用户。
                }
            }
        }
        transactions.cleanup(
                now.minus(properties.auditRetention()),
                properties.cleanupBatchSize());
    }

    private void executeClaimed(AccountCancellation cancellation) {
        String cancellationId = Long.toString(cancellation.id());
        try {
            TrackingAccountCancellationResponse tracking = trackingDuration.record(() ->
                    trackingClient.cancelAccount(
                            cancellation.userId(), cancellationId,
                            new TrackingAccountCancellationCommand(cancellationId)));
            ReminderAccountCancellationResponse reminder = reminderDuration.record(() ->
                    reminderClient.cancelAccount(
                            cancellation.userId(), cancellationId,
                            new ReminderAccountCancellationCommand(cancellationId)));
            if (!tracking.completed() || !reminder.completed()) {
                throw new IllegalStateException("账号注销下游未确认完成");
            }
            transactions.finalizeExecution(cancellation, now());
            success.increment();
        } catch (RuntimeException exception) {
            retry.increment();
            LOGGER.warn(
                    "账号注销执行等待整组重试 cancellationId={}, userId={}, failureType={}",
                    cancellation.id(),
                    cancellation.userId(),
                    exception.getClass().getSimpleName());
            throw exception;
        }
    }

    private static Counter counter(MeterRegistry registry, String result) {
        Objects.requireNonNull(registry, "指标注册器不能为空");
        return Counter.builder(METRIC).tag("result", result).register(registry);
    }

    private static Timer downstreamTimer(MeterRegistry registry, String service) {
        return Timer.builder(METRIC + ".downstream.duration")
                .tag("service", service)
                .register(registry);
    }

    private static void registerGauge(
            MeterRegistry registry, String status, AtomicLong value) {
        Gauge.builder(METRIC + ".tasks", value, AtomicLong::get)
                .tag("status", status)
                .register(registry);
    }

    private void refreshBacklogMetrics(LocalDateTime now) {
        try {
            pendingTasks.set(store.countByStatus(PENDING));
            executingTasks.set(store.countByStatus(EXECUTING));
            oldestOpenAgeSeconds.set(store.findOldestOpenApplyAt()
                    .map(oldest -> Math.max(0L, Duration.between(oldest, now).toSeconds()))
                    .orElse(0L));
        } catch (RuntimeException exception) {
            LOGGER.warn("账号注销积压指标刷新失败 failureType={}",
                    exception.getClass().getSimpleName());
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);
    }
}
