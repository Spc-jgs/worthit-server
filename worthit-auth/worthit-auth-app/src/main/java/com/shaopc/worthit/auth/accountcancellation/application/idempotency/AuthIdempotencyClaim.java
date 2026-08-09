package com.shaopc.worthit.auth.accountcancellation.application.idempotency;

import java.time.LocalDateTime;

/** Auth 幂等 claim 结果。 */
public record AuthIdempotencyClaim<T>(
        Status status,
        T replay,
        String errorCode,
        String errorMessage,
        LocalDateTime leaseExpiresAt) {

    public enum Status {
        NEW,
        REPLAY_SUCCESS,
        REPLAY_FAILURE,
        IN_PROGRESS,
        CONFLICT
    }
}
