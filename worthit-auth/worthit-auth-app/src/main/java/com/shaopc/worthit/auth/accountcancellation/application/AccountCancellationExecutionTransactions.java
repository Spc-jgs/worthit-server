package com.shaopc.worthit.auth.accountcancellation.application;

import com.shaopc.worthit.auth.accountcancellation.application.port.AccountCancellationStore;
import com.shaopc.worthit.auth.accountcancellation.domain.AccountCancellation;
import com.shaopc.worthit.auth.accountcancellation.domain.AccountCancellationStatus;
import com.shaopc.worthit.auth.authentication.application.port.AuthUserRepository;
import com.shaopc.worthit.auth.authentication.application.port.UserSession;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/** 将 claim、最终清理与保留清理固定在独立短事务中。 */
@Component
public class AccountCancellationExecutionTransactions {

    private final AccountCancellationStore store;
    private final AuthUserRepository userRepository;
    private final UserSession userSession;

    public AccountCancellationExecutionTransactions(
            AccountCancellationStore store,
            AuthUserRepository userRepository,
            UserSession userSession) {
        this.store = store;
        this.userRepository = userRepository;
        this.userSession = userSession;
    }

    /** 以 auth_user -> cancellation 固定锁顺序竞争到期执行权。 */
    @Transactional
    public Optional<AccountCancellation> claim(
            AccountCancellation candidate, LocalDateTime now) {
        if (userRepository.findByIdForUpdate(candidate.userId()).isEmpty()) {
            return Optional.empty();
        }
        AccountCancellation locked = store
                .findForUpdate(candidate.id(), candidate.userId())
                .orElse(null);
        if (locked == null) {
            return Optional.empty();
        }
        if (locked.status() == AccountCancellationStatus.EXECUTING) {
            userSession.logoutUser(locked.userId());
            return Optional.of(locked);
        }
        if (locked.status() != AccountCancellationStatus.PENDING
                || locked.effectiveAt().isAfter(now)) {
            return Optional.empty();
        }
        if (!store.markUserExecuting(locked.userId(), now)
                || !store.claimExecution(
                        locked.id(), locked.userId(), locked.version(), now)) {
            throw new IllegalStateException("账号注销到期 claim 失败");
        }
        userSession.logoutUser(locked.userId());
        return Optional.of(new AccountCancellation(
                locked.id(),
                locked.userId(),
                locked.applyAt(),
                locked.effectiveAt(),
                null,
                AccountCancellationStatus.EXECUTING,
                null,
                locked.version() + 1));
    }

    /** 两个下游均完成后，在一个本地事务中删除 Auth 身份并完成状态。 */
    @Transactional
    public void finalizeExecution(AccountCancellation candidate, LocalDateTime now) {
        if (userRepository.findByIdForUpdate(candidate.userId()).isEmpty()) {
            throw new IllegalStateException("执行态 Auth 用户不存在");
        }
        AccountCancellation locked = store
                .findForUpdate(candidate.id(), candidate.userId())
                .orElseThrow(() -> new IllegalStateException("执行态注销记录不存在"));
        if (locked.status() != AccountCancellationStatus.EXECUTING) {
            return;
        }
        userSession.logoutUser(locked.userId());
        store.finalizeCancellation(
                locked.id(), locked.userId(), locked.version(), now);
    }

    /** 小批量删除超过保留期的完成/撤销记录。 */
    @Transactional
    public int cleanup(LocalDateTime cutoff, int limit) {
        return store.deleteTerminalBefore(cutoff, limit);
    }
}
