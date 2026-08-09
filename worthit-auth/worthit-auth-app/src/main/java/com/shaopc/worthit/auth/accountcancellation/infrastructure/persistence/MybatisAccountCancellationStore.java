package com.shaopc.worthit.auth.accountcancellation.infrastructure.persistence;

import com.shaopc.worthit.auth.accountcancellation.application.port.AccountCancellationStore;
import com.shaopc.worthit.auth.accountcancellation.domain.AccountCancellation;
import com.shaopc.worthit.auth.accountcancellation.domain.AccountCancellationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** MyBatis 账号注销持久化适配器。 */
@Repository
@RequiredArgsConstructor
public class MybatisAccountCancellationStore implements AccountCancellationStore {

    private final AccountCancellationMapper mapper;

    @Override
    public Optional<AccountCancellation> findOpenForUpdate(long userId) {
        return Optional.ofNullable(mapper.selectOpenForUpdate(userId)).map(this::toDomain);
    }

    @Override
    public Optional<AccountCancellation> findLatest(long userId) {
        return Optional.ofNullable(mapper.selectLatest(userId)).map(this::toDomain);
    }

    @Override
    public Optional<AccountCancellation> findForUpdate(long cancellationId, long userId) {
        return Optional.ofNullable(mapper.selectForUpdate(cancellationId, userId))
                .map(this::toDomain);
    }

    @Override
    public Optional<AccountCancellation> findById(long cancellationId) {
        return Optional.ofNullable(mapper.selectById(cancellationId)).map(this::toDomain);
    }

    @Override
    public AccountCancellation create(
            long cancellationId,
            long userId,
            LocalDateTime applyAt,
            LocalDateTime effectiveAt) {
        AccountCancellationDO value = new AccountCancellationDO();
        value.setId(cancellationId);
        value.setUserId(userId);
        value.setApplyAt(applyAt);
        value.setEffectiveAt(effectiveAt);
        if (mapper.insert(value) != 1) {
            throw new IllegalStateException("账号注销申请写入失败");
        }
        return new AccountCancellation(
                cancellationId,
                userId,
                applyAt,
                effectiveAt,
                null,
                AccountCancellationStatus.PENDING,
                null,
                1);
    }

    @Override
    public boolean revoke(
            long cancellationId,
            long userId,
            long expectedVersion,
            LocalDateTime revokedAt) {
        return mapper.revoke(cancellationId, userId, expectedVersion, revokedAt) == 1;
    }

    @Override
    public List<AccountCancellation> findExecutable(LocalDateTime now, int limit) {
        return mapper.selectExecutable(now, limit).stream().map(this::toDomain).toList();
    }

    @Override
    public long countByStatus(AccountCancellationStatus status) {
        return mapper.countByStatus(status.name());
    }

    @Override
    public Optional<LocalDateTime> findOldestOpenApplyAt() {
        return Optional.ofNullable(mapper.selectOldestOpenApplyAt());
    }

    @Override
    public boolean claimExecution(
            long cancellationId,
            long userId,
            long expectedVersion,
            LocalDateTime now) {
        return mapper.claimExecution(
                cancellationId, userId, expectedVersion, now) == 1;
    }

    @Override
    public boolean markUserExecuting(long userId, LocalDateTime now) {
        return mapper.markUserExecuting(userId, now) == 1;
    }

    @Override
    public void finalizeCancellation(
            long cancellationId,
            long userId,
            long expectedVersion,
            LocalDateTime completedAt) {
        mapper.deletePasswordCredential(userId);
        mapper.deleteExternalIdentities(userId);
        mapper.deleteLoginAudits(userId);
        mapper.deleteIdempotency(userId);
        if (mapper.deleteExecutingUser(userId) != 1) {
            throw new IllegalStateException("执行态 Auth 用户物理删除失败");
        }
        if (mapper.complete(cancellationId, userId, expectedVersion, completedAt) != 1) {
            throw new IllegalStateException("账号注销状态完成失败");
        }
    }

    @Override
    public int deleteTerminalBefore(LocalDateTime cutoff, int limit) {
        return mapper.deleteTerminalBefore(cutoff, limit);
    }

    private AccountCancellation toDomain(AccountCancellationDO value) {
        return new AccountCancellation(
                value.getId(),
                value.getUserId(),
                value.getApplyAt(),
                value.getEffectiveAt(),
                value.getCompletedAt(),
                AccountCancellationStatus.valueOf(value.getStatus()),
                value.getRevokedAt(),
                value.getVersion());
    }
}
