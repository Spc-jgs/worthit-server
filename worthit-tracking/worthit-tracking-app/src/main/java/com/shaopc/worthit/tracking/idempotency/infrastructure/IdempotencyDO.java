package com.shaopc.worthit.tracking.idempotency.infrastructure;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * {@code trk_idempotency_record} 持久化对象。
 */
@Getter
@Setter
@NoArgsConstructor
public class IdempotencyDO {

    private Long id;
    private Long userId;
    private String operationCode;
    private String idempotencyKey;
    private String requestHash;
    private String responseJson;
    private String status;
    private LocalDateTime processingExpireAt;
    private LocalDateTime expiresAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
