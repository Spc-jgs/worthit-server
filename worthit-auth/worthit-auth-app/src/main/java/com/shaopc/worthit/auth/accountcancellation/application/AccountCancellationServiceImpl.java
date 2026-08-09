package com.shaopc.worthit.auth.accountcancellation.application;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.shaopc.worthit.auth.accountcancellation.application.idempotency.AuthCancellationOperation;
import com.shaopc.worthit.auth.accountcancellation.application.idempotency.AuthIdempotencyExecutor;
import com.shaopc.worthit.auth.accountcancellation.application.port.AccountCancellationStore;
import com.shaopc.worthit.auth.accountcancellation.domain.AccountCancellation;
import com.shaopc.worthit.auth.accountcancellation.domain.AccountCancellationErrorCode;
import com.shaopc.worthit.auth.accountcancellation.domain.AccountCancellationStatus;
import com.shaopc.worthit.auth.accountcancellation.interfaces.rest.AccountCancellationResponse;
import com.shaopc.worthit.auth.accountcancellation.interfaces.rest.AccountCancellationStatusResponse;
import com.shaopc.worthit.auth.authentication.application.port.AuthUserRepository;
import com.shaopc.worthit.auth.authentication.application.port.UserSession;
import com.shaopc.worthit.auth.authentication.domain.AuthUser;
import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.security.error.SecurityErrorCode;
import com.shaopc.worthit.common.web.error.CommonWebErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

/** 以 auth_user 行锁和持久幂等状态机实现账号注销公网用例。 */
@Service
@RequiredArgsConstructor
public class AccountCancellationServiceImpl implements AccountCancellationService {

    private static final long COOLING_DAYS = 7;
    private final AccountCancellationStore store;
    private final AuthIdempotencyExecutor idempotencyExecutor;
    private final AuthUserRepository userRepository;
    private final UserSession userSession;
    private final Clock clock;

    @Override
    public AccountCancellationResponse apply(String idempotencyKey) {
        String key = requireUuid(idempotencyKey);
        long userId = userSession.currentUserId();
        return idempotencyExecutor.execute(
                userId,
                AuthCancellationOperation.APPLY,
                key,
                digest("apply:v1"),
                AccountCancellationResponse.class,
                () -> applyNew(userId));
    }

    @Transactional(readOnly = true)
    @Override
    public AccountCancellationStatusResponse status() {
        return new AccountCancellationStatusResponse(
                store.findLatest(userSession.currentUserId())
                        .map(AccountCancellationServiceImpl::toResponse)
                        .orElse(null));
    }

    @Override
    public AccountCancellationResponse revoke(
            String idempotencyKey, String cancellationId, long version) {
        String key = requireUuid(idempotencyKey);
        long parsedId = parsePositiveId(cancellationId);
        if (version <= 0) {
            throw invalid();
        }
        long userId = userSession.currentUserId();
        return idempotencyExecutor.execute(
                userId,
                AuthCancellationOperation.REVOKE,
                key,
                digest("revoke:v1|" + parsedId + "|" + version),
                AccountCancellationResponse.class,
                () -> revokePending(userId, parsedId, version));
    }

    private AccountCancellationResponse applyNew(long userId) {
        requireActiveLockedUser(userId);
        return store.findOpenForUpdate(userId)
                .map(AccountCancellationServiceImpl::toResponse)
                .orElseGet(() -> {
                    LocalDateTime applyAt = now();
                    return toResponse(store.create(
                            IdWorker.getId(),
                            userId,
                            applyAt,
                            applyAt.plusDays(COOLING_DAYS)));
                });
    }

    private AccountCancellationResponse revokePending(
            long userId, long cancellationId, long version) {
        requireRevocableLockedUser(userId);
        AccountCancellation cancellation = store
                .findForUpdate(cancellationId, userId)
                .orElseThrow(AccountCancellationServiceImpl::notFound);
        if (cancellation.status() == AccountCancellationStatus.REVOKED) {
            return toResponse(cancellation);
        }
        if (cancellation.status() == AccountCancellationStatus.COMPLETED) {
            throw notFound();
        }
        LocalDateTime now = now();
        if (cancellation.status() != AccountCancellationStatus.PENDING
                || cancellation.version() != version
                || !now.isBefore(cancellation.effectiveAt())) {
            throw stateConflict();
        }
        if (!store.revoke(cancellationId, userId, version, now)) {
            throw stateConflict();
        }
        return toResponse(new AccountCancellation(
                cancellation.id(),
                cancellation.userId(),
                cancellation.applyAt(),
                cancellation.effectiveAt(),
                null,
                AccountCancellationStatus.REVOKED,
                now,
                version + 1));
    }

    private void requireActiveLockedUser(long userId) {
        AuthUser user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(AccountCancellationServiceImpl::notFound);
        if (!user.active()) {
            throw new BusinessException(SecurityErrorCode.AUTH_FORBIDDEN);
        }
    }

    private void requireRevocableLockedUser(long userId) {
        AuthUser user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(AccountCancellationServiceImpl::notFound);
        if (!user.active()) {
            throw stateConflict();
        }
    }

    private static AccountCancellationResponse toResponse(
            AccountCancellation cancellation) {
        return new AccountCancellationResponse(
                Long.toString(cancellation.id()),
                cancellation.status().name(),
                cancellation.applyAt(),
                cancellation.effectiveAt(),
                cancellation.revokedAt(),
                cancellation.completedAt(),
                cancellation.version());
    }

    private static String requireUuid(String value) {
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equalsIgnoreCase(value)) {
                throw invalid();
            }
            return parsed.toString();
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
    }

    private static long parsePositiveId(String value) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed > 0) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
            // 统一转换为稳定参数错误。
        }
        throw invalid();
    }

    private static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境缺少 SHA-256", exception);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);
    }

    private static BusinessException invalid() {
        return new BusinessException(CommonWebErrorCode.VAL_INVALID_ARGUMENT);
    }

    private static BusinessException notFound() {
        return new BusinessException(CommonWebErrorCode.RES_NOT_FOUND);
    }

    private static BusinessException stateConflict() {
        return new BusinessException(
                AccountCancellationErrorCode.VAL_STATE_CONFLICT);
    }
}
