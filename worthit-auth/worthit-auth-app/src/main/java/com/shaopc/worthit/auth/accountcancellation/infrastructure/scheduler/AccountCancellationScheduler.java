package com.shaopc.worthit.auth.accountcancellation.infrastructure.scheduler;

import com.shaopc.worthit.auth.accountcancellation.application.AccountCancellationExecutionService;
import org.springframework.scheduling.annotation.Scheduled;

/** Redis 仅用于减少重复扫描，数据库状态仍是正确性边界。 */
public class AccountCancellationScheduler {

    private static final String LOCK_NAME = "worthit:auth:account-cancellation";
    private final AccountCancellationExecutionService service;
    private final AccountCancellationLeaderLock leaderLock;
    private final AccountCancellationProperties properties;

    public AccountCancellationScheduler(
            AccountCancellationExecutionService service,
            AccountCancellationLeaderLock leaderLock,
            AccountCancellationProperties properties) {
        this.service = service;
        this.leaderLock = leaderLock;
        this.properties = properties;
    }

    @Scheduled(
            fixedDelayString =
                    "${worthit.account-cancellation.check-interval:30s}")
    public void executeIfLeader() {
        if (!properties.enabled()) {
            return;
        }
        leaderLock.executeIfLeader(
                LOCK_NAME, properties.lockTtl(), service::processBatch);
    }
}
