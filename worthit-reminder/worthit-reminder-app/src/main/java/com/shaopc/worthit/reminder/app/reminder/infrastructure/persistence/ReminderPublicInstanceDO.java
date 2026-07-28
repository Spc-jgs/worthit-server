package com.shaopc.worthit.reminder.app.reminder.infrastructure.persistence;

import java.time.LocalDateTime;

/**
 * Reminder 忽略用例锁定行。
 */
public class ReminderPublicInstanceDO {

    private Long id;
    private String status;
    private LocalDateTime remindAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getRemindAt() {
        return remindAt;
    }

    public void setRemindAt(LocalDateTime remindAt) {
        this.remindAt = remindAt;
    }
}
