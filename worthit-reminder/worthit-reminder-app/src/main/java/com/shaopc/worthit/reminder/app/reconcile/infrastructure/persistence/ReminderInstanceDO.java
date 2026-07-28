package com.shaopc.worthit.reminder.app.reconcile.infrastructure.persistence;

import java.time.LocalDateTime;

/**
 * rem_instance 当前 PENDING 查询对象。
 */
public class ReminderInstanceDO {

    private Long id;
    private LocalDateTime remindAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getRemindAt() {
        return remindAt;
    }

    public void setRemindAt(LocalDateTime remindAt) {
        this.remindAt = remindAt;
    }
}
