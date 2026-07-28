package com.shaopc.worthit.reminder.app.reconcile.domain;

import com.shaopc.worthit.common.core.error.ErrorCode;

/**
 * Reminder reconcile 稳定业务错误码。
 */
public enum ReminderErrorCode implements ErrorCode {

    /** 同一事件或来源版本对应了不同的期望状态。 */
    BIZ_CONTRACT_CONFLICT(
            "BIZ_CONTRACT_CONFLICT",
            "提醒协调契约冲突");

    private final String code;
    private final String defaultMessage;

    ReminderErrorCode(
            String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }
}
