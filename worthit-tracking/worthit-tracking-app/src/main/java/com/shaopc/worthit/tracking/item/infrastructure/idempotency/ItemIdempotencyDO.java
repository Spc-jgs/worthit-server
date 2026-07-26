package com.shaopc.worthit.tracking.item.infrastructure.idempotency;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * {@code trk_idempotency_record} 的 Item 创建投影。
 */
@Getter
@Setter
@NoArgsConstructor
public class ItemIdempotencyDO {

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
