package com.shaopc.worthit.tracking.idempotency.application;

import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.core.error.ErrorCode;
import com.shaopc.worthit.common.web.error.CommonWebErrorCode;
import com.shaopc.worthit.tracking.accountcancellation.application.TrackingUserWriteFence;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.Set;

/**
 * 以短 claim 事务、业务事务和失败落库事务完成幂等执行。
 */
@Service
public class IdempotencyExecutionCoordinatorImpl
        implements IdempotencyExecutionCoordinator {

    private static final Set<String> RETRYABLE_ERROR_CODES =
            Set.of(
                    CommonWebErrorCode.SYS_ERROR.code(),
                    CommonWebErrorCode.SYS_UPSTREAM.code());

    private final IdempotencyExecutionStore store;
    private final TrackingUserWriteFence userWriteFence;
    private final TransactionTemplate claimTransaction;
    private final TransactionTemplate businessTransaction;

    public IdempotencyExecutionCoordinatorImpl(
            IdempotencyExecutionStore store,
            TrackingUserWriteFence userWriteFence,
            PlatformTransactionManager transactionManager) {
        this.store = Objects.requireNonNull(
                store, "幂等执行存储不能为空");
        this.userWriteFence = Objects.requireNonNull(
                userWriteFence, "用户写围栏不能为空");
        Objects.requireNonNull(
                transactionManager, "事务管理器不能为空");
        this.claimTransaction =
                new TransactionTemplate(transactionManager);
        this.claimTransaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.businessTransaction =
                new TransactionTemplate(transactionManager);
        this.businessTransaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRED);
    }

    @Override
    public <T> T execute(
            long userId,
            TrackingOperation operation,
            String idempotencyKey,
            String requestHash,
            Class<T> responseType,
            IdempotentAction<T> action) {
        IdempotencyExecutionClaim<T> claim =
                Objects.requireNonNull(
                        claimTransaction.execute(status ->
                                store.claim(
                                        userId,
                                        operation,
                                        idempotencyKey,
                                        requestHash,
                                        responseType)),
                        "幂等占位结果不能为空");
        return switch (claim.status()) {
            case REPLAY_SUCCESS -> Objects.requireNonNull(
                    claim.replay(), "幂等成功重放结果不能为空");
            case REPLAY_FAILURE -> throw replayFailure(claim);
            case IN_PROGRESS -> throw new BusinessException(
                    IdempotencyErrorCode.IDEM_IN_PROGRESS);
            case CONFLICT -> throw new BusinessException(
                    IdempotencyErrorCode.IDEM_CONFLICT);
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
            TrackingOperation operation,
            String idempotencyKey,
            String requestHash,
            IdempotencyExecutionClaim<T> claim,
            IdempotentAction<T> action) {
        try {
            return Objects.requireNonNull(
                    businessTransaction.execute(status -> {
                        userWriteFence.requireActive(userId);
                        T response = Objects.requireNonNull(
                                action.execute(),
                                "幂等业务响应不能为空");
                        store.completeSuccess(
                                userId,
                                operation,
                                idempotencyKey,
                                requestHash,
                                requiredLease(claim),
                                response);
                        return response;
                    }),
                    "幂等业务事务结果不能为空");
        } catch (BusinessException exception) {
            if (RETRYABLE_ERROR_CODES.contains(
                    exception.code())) {
                throw exception;
            }
            persistFailure(
                    userId,
                    operation,
                    idempotencyKey,
                    requestHash,
                    claim,
                    exception);
            throw exception;
        }
    }

    private void persistFailure(
            long userId,
            TrackingOperation operation,
            String idempotencyKey,
            String requestHash,
            IdempotencyExecutionClaim<?> claim,
            BusinessException businessException) {
        try {
            claimTransaction.executeWithoutResult(status ->
                    store.completeFailure(
                            userId,
                            operation,
                            idempotencyKey,
                            requestHash,
                            requiredLease(claim),
                            businessException.code(),
                            businessException.getMessage()));
        } catch (RuntimeException persistenceException) {
            persistenceException.addSuppressed(
                    businessException);
            throw persistenceException;
        }
    }

    private static BusinessException replayFailure(
            IdempotencyExecutionClaim<?> claim) {
        String code = Objects.requireNonNull(
                claim.errorCode(),
                "幂等失败重放错误码不能为空");
        String message = Objects.requireNonNull(
                claim.errorMessage(),
                "幂等失败重放消息不能为空");
        return new BusinessException(
                new StoredErrorCode(code, message));
    }

    private static java.time.LocalDateTime requiredLease(
            IdempotencyExecutionClaim<?> claim) {
        return Objects.requireNonNull(
                claim.leaseExpiresAt(),
                "新幂等执行租约不能为空");
    }

    private record StoredErrorCode(
            String code,
            String defaultMessage) implements ErrorCode {
    }
}
