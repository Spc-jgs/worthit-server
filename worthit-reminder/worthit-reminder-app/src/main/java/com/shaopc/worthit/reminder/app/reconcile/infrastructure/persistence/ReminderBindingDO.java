package com.shaopc.worthit.reminder.app.reconcile.infrastructure.persistence;

/**
 * rem_binding 查询对象。
 */
public class ReminderBindingDO {

    private Long id;
    private Boolean reminderEnabled;
    private Long lastSourceVersion;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Boolean getReminderEnabled() {
        return reminderEnabled;
    }

    public void setReminderEnabled(Boolean reminderEnabled) {
        this.reminderEnabled = reminderEnabled;
    }

    public Long getLastSourceVersion() {
        return lastSourceVersion;
    }

    public void setLastSourceVersion(Long lastSourceVersion) {
        this.lastSourceVersion = lastSourceVersion;
    }
}
