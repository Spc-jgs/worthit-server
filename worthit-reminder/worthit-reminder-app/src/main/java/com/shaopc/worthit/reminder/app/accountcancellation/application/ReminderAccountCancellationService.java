package com.shaopc.worthit.reminder.app.accountcancellation.application;

import com.shaopc.worthit.reminder.client.response.ReminderAccountCancellationResponse;

/** Reminder 账号注销本地清理用例。 */
public interface ReminderAccountCancellationService {

    /** 幂等清理指定用户全部 Reminder 数据。 */
    ReminderAccountCancellationResponse cancel(long userId, String cancellationId);
}
