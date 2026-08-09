package com.shaopc.worthit.auth.accountcancellation.application.idempotency;

/** 以持久幂等状态机执行 Auth 账号注销公网写命令。 */
public interface AuthIdempotencyExecutor {

    <T> T execute(
            long userId,
            AuthCancellationOperation operation,
            String idempotencyKey,
            String requestHash,
            Class<T> responseType,
            IdempotentAction<T> action);

    @FunctionalInterface
    interface IdempotentAction<T> {
        T execute();
    }
}
