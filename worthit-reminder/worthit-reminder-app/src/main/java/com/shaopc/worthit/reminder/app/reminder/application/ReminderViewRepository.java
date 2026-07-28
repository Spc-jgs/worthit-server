package com.shaopc.worthit.reminder.app.reminder.application;

import com.shaopc.worthit.common.core.pagination.PageQuery;
import com.shaopc.worthit.common.core.pagination.PageResult;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Reminder 公网查询和忽略持久化边界。
 */
public interface ReminderViewRepository {

    /**
     * 按冻结页签查询当前用户提醒。
     */
    PageResult<ReminderListItem> list(
            long userId,
            ReminderTab tab,
            LocalDateTime now,
            PageQuery pageQuery);

    /**
     * 统计当前用户已到期 PENDING 数量。
     */
    long countPending(
            long userId,
            LocalDateTime now);

    /**
     * 锁定当前用户可见的提醒实例。
     */
    Optional<ReminderInstanceState> findByIdForUpdate(
            long userId,
            long reminderId);

    /**
     * 条件更新已到期 PENDING 为 IGNORED。
     */
    boolean ignore(
            long userId,
            long reminderId,
            LocalDateTime now);
}
