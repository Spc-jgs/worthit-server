package com.shaopc.worthit.tracking.accountcancellation.infrastructure.persistence;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** trk_user_write_fence 锁定结果。 */
@Getter
@Setter
public class TrackingUserWriteFenceDO {

    private Long userId;
    private String status;
    private String cancellationId;
    private LocalDateTime completedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
