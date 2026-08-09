package com.shaopc.worthit.auth.accountcancellation.application.idempotency;

import java.time.LocalDateTime;

/** Auth 账号注销持久幂等存储端口。 */
public interface AuthIdempotencyStore {

    <T> AuthIdempotencyClaim<T> claim(
            long userId,
            AuthCancellationOperation operation,
            String idempotencyKey,
            String requestHash,
            Class<T> responseType);

    <T> void completeSuccess(
            long userId,
            AuthCancellationOperation operation,
            String idempotencyKey,
            String requestHash,
            LocalDateTime leaseExpiresAt,
            T response);

    void completeFailure(
            long userId,
            AuthCancellationOperation operation,
            String idempotencyKey,
            String requestHash,
            LocalDateTime leaseExpiresAt,
            String errorCode,
            String errorMessage);
}
