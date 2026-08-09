package com.shaopc.worthit.auth.accountcancellation.application.idempotency;

import com.shaopc.worthit.auth.accountcancellation.domain.AccountCancellationErrorCode;
import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.core.error.ErrorCode;
import com.shaopc.worthit.common.web.error.CommonWebErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

/** 以短 claim 事务、业务事务和失败事务编排持久幂等。 */
@Service
public class AuthIdempotencyExecutorImpl implements AuthIdempotencyExecutor {

    private static final Set<String> RETRYABLE_ERROR_CODES = Set.of(
            CommonWebErrorCode.SYS_ERROR.code(),
            CommonWebErrorCode.SYS_UPSTREAM.code());

    private final AuthIdempotencyStore store;
    private final TransactionTemplate claimTransaction;
    private final TransactionTemplate businessTransaction;

    public AuthIdempotencyExecutorImpl(
            AuthIdempotencyStore store,
            PlatformTransactionManager transactionManager) {
        this.store = Objects.requireNonNull(store, "Auth 幂等存储不能为空");
        Objects.requireNonNull(transactionManager, "事务管理器不能为空");
        claimTransaction = new TransactionTemplate(transactionManager);
        claimTransaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        businessTransaction = new TransactionTemplate(transactionManager);
        businessTransaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRED);
    }

    @Override
    public <T> T execute(
            long userId,
            AuthCancellationOperation operation,
            String idempotencyKey,
            String requestHash,
            Class<T> responseType,
            IdempotentAction<T> action) {
        AuthIdempotencyClaim<T> claim = Objects.requireNonNull(
                claimTransaction.execute(status -> store.claim(
                        userId,
                        operation,
                        idempotencyKey,
                        requestHash,
                        responseType)),
                "Auth 幂等 claim 结果不能为空");
        return switch (claim.status()) {
            case REPLAY_SUCCESS -> Objects.requireNonNull(claim.replay());
            case REPLAY_FAILURE -> throw replayFailure(claim);
            case IN_PROGRESS -> throw new BusinessException(
                    AccountCancellationErrorCode.IDEM_IN_PROGRESS);
            case CONFLICT -> throw new BusinessException(
                    AccountCancellationErrorCode.IDEM_CONFLICT);
            case NEW -> executeNew(
                    userId,
                    operation,
                    idempotencyKey,
                    requestHash,
                    claim,
                    action);
        };
    }

    private <T> T executeNew(
            long userId,
            AuthCancellationOperation operation,
            String idempotencyKey,
            String requestHash,
            AuthIdempotencyClaim<T> claim,
            IdempotentAction<T> action) {
        try {
            return Objects.requireNonNull(
                    businessTransaction.execute(status -> {
                        T response = Objects.requireNonNull(action.execute());
                        store.completeSuccess(
                                userId,
                                operation,
                                idempotencyKey,
                                requestHash,
                                requiredLease(claim),
                                response);
                        return response;
                    }));
        } catch (BusinessException exception) {
            if (RETRYABLE_ERROR_CODES.contains(exception.code())) {
                throw exception;
            }
            claimTransaction.executeWithoutResult(status -> store.completeFailure(
                    userId,
                    operation,
                    idempotencyKey,
                    requestHash,
                    requiredLease(claim),
                    exception.code(),
                    exception.getMessage()));
            throw exception;
        }
    }

    private static BusinessException replayFailure(AuthIdempotencyClaim<?> claim) {
        return new BusinessException(new StoredErrorCode(
                Objects.requireNonNull(claim.errorCode()),
                Objects.requireNonNull(claim.errorMessage())));
    }

    private static LocalDateTime requiredLease(AuthIdempotencyClaim<?> claim) {
        return Objects.requireNonNull(claim.leaseExpiresAt());
    }

    private record StoredErrorCode(
            String code,
            String defaultMessage) implements ErrorCode {
    }
}
