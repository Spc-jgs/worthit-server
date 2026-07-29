package com.shaopc.worthit.reminder.app.reminder.application;

import com.shaopc.worthit.common.core.pagination.PageResult;

/**
 * 提醒中心公开应用用例。
 */
public interface ReminderViewService {

    /**
     * 分页查询当前用户可见提醒。
     */
    PageResult<ReminderListItem> list(
            ReminderTab tab,
            int page,
            int size);

    /**
     * 统计当前用户已到期的待处理提醒。
     */
    long pendingCount();

    /**
     * 幂等忽略当前用户一条已到期提醒。
     */
    void ignore(long reminderId);
}
