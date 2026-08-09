package com.shaopc.worthit.auth.accountcancellation.infrastructure.idempotency;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** auth_idempotency_record 持久化对象。 */
@Getter
@Setter
public class AuthIdempotencyDO {
    private Long id;
    private Long userId;
    private String operationCode;
    private String idempotencyKey;
    private String requestHash;
    private String responseJson;
    private String status;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime processingExpireAt;
    private LocalDateTime expiresAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
