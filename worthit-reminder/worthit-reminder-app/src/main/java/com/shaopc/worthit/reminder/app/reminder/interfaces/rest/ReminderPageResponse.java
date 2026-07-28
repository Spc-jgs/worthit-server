package com.shaopc.worthit.reminder.app.reminder.interfaces.rest;

import java.util.List;

/**
 * 提醒中心分页响应。
 */
public record ReminderPageResponse(
        List<ReminderItemResponse> items,
        int page,
        int size,
        long total,
        boolean hasMore) {
}
