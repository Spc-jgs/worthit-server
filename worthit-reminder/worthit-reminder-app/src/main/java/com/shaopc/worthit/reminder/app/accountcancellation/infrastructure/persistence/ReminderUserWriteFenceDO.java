package com.shaopc.worthit.reminder.app.accountcancellation.infrastructure.persistence;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** rem_user_write_fence 锁定结果。 */
@Getter
@Setter
public class ReminderUserWriteFenceDO {

    private Long userId;
    private String status;
    private String cancellationId;
    private LocalDateTime completedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
