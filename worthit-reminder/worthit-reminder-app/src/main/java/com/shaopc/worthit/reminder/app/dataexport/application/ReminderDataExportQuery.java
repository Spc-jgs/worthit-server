package com.shaopc.worthit.reminder.app.dataexport.application;

import com.shaopc.worthit.reminder.client.response.ReminderDataExportResponse;

import java.util.List;

/**
 * 按用户、主键升序读取 Reminder 导出记录的应用端口。
 */
public interface ReminderDataExportQuery {

    List<ReminderDataExportResponse.Binding> bindings(long userId, int limit);

    List<ReminderDataExportResponse.Instance> instances(long userId, int limit);
}
