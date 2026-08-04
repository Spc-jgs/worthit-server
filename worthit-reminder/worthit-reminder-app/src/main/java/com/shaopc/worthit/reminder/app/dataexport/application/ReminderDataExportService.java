package com.shaopc.worthit.reminder.app.dataexport.application;

import com.shaopc.worthit.reminder.client.response.ReminderDataExportResponse;

/** Reminder 按数据所有权生成用户导出分片的应用用例。 */
public interface ReminderDataExportService {

    /** 在 Reminder 本地一致快照中导出指定用户的提醒全状态历史。 */
    ReminderDataExportResponse exportUserData(long userId);
}
