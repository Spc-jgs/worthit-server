package com.shaopc.worthit.auth.accountcancellation.application.port;

import com.shaopc.worthit.auth.accountcancellation.domain.AccountCancellation;
import com.shaopc.worthit.auth.accountcancellation.domain.AccountCancellationStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 账号注销状态、Auth 用户状态和最终身份清理持久化端口。 */
public interface AccountCancellationStore {

    Optional<AccountCancellation> findOpenForUpdate(long userId);

    Optional<AccountCancellation> findLatest(long userId);

    Optional<AccountCancellation> findForUpdate(long cancellationId, long userId);

    Optional<AccountCancellation> findById(long cancellationId);

    AccountCancellation create(
            long cancellationId,
            long userId,
            LocalDateTime applyAt,
            LocalDateTime effectiveAt);

    boolean revoke(
            long cancellationId,
            long userId,
            long expectedVersion,
            LocalDateTime revokedAt);

    List<AccountCancellation> findExecutable(LocalDateTime now, int limit);

    long countByStatus(AccountCancellationStatus status);

    Optional<LocalDateTime> findOldestOpenApplyAt();

    boolean claimExecution(
            long cancellationId,
            long userId,
            long expectedVersion,
            LocalDateTime now);

    boolean markUserExecuting(long userId, LocalDateTime now);

    void finalizeCancellation(
            long cancellationId,
            long userId,
            long expectedVersion,
            LocalDateTime completedAt);

    int deleteTerminalBefore(LocalDateTime cutoff, int limit);
}
