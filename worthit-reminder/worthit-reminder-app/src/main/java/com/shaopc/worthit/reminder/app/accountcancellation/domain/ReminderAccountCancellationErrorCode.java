package com.shaopc.worthit.reminder.app.accountcancellation.domain;

import com.shaopc.worthit.common.core.error.ErrorCode;

/** Reminder 账号注销与用户写围栏稳定错误码。 */
public enum ReminderAccountCancellationErrorCode implements ErrorCode {

    /** 用户已进入注销流程，或注销标识与现有围栏冲突。 */
    VAL_STATE_CONFLICT("VAL_STATE_CONFLICT", "账号注销状态冲突");

    private final String code;
    private final String defaultMessage;

    ReminderAccountCancellationErrorCode(String code, String defaultMessage) {
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
