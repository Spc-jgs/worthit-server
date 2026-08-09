package com.shaopc.worthit.reminder.app.reminder.application;

import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.core.pagination.PageQuery;
import com.shaopc.worthit.common.core.pagination.PageResult;
import com.shaopc.worthit.common.web.error.CommonWebErrorCode;
import com.shaopc.worthit.reminder.app.accountcancellation.application.ReminderUserWriteFence;
import com.shaopc.worthit.reminder.app.reconcile.domain.ReminderErrorCode;
import com.shaopc.worthit.reminder.app.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 提醒中心列表、计数与忽略用例。
 */
@Service
public class ReminderViewServiceImpl implements ReminderViewService {

    private final ReminderViewRepository repository;
    private final ReminderUserWriteFence userWriteFence;
    private final CurrentUserProvider currentUserProvider;
    private final Clock reminderClock;

    /**
     * 创建 Reminder 公网应用服务。
     */
    public ReminderViewServiceImpl(
            ReminderViewRepository repository,
            ReminderUserWriteFence userWriteFence,
            CurrentUserProvider currentUserProvider,
            Clock reminderClock) {
        this.repository = repository;
        this.userWriteFence = userWriteFence;
        this.currentUserProvider = currentUserProvider;
        this.reminderClock = reminderClock;
    }

    /**
     * 分页查询当前用户可见提醒。
     */
    @Transactional(readOnly = true)
    @Override
    public PageResult<ReminderListItem> list(
            ReminderTab tab,
            int page,
            int size) {
        return repository.list(
                currentUserId(),
                tab,
                now(),
                new PageQuery(page, size));
    }

    /**
     * 统计当前用户已到期 PENDING。
     */
    @Transactional(readOnly = true)
    @Override
    public long pendingCount() {
        return repository.countPending(
                currentUserId(), now());
    }

    /**
     * 幂等忽略当前用户一条已到期 PENDING。
     */
    @Transactional
    @Override
    public void ignore(long reminderId) {
        long userId = currentUserId();
        userWriteFence.requireActive(userId);
        LocalDateTime now = now();
        ReminderInstanceState instance =
                repository.findByIdForUpdate(
                                userId, reminderId)
                        .orElseThrow(() ->
                                new BusinessException(
                                        CommonWebErrorCode
                                                .RES_NOT_FOUND));
        if ("IGNORED".equals(instance.status())) {
            return;
        }
        if (!"PENDING".equals(instance.status())
                || instance.remindAt().isAfter(now)) {
            throw new BusinessException(
                    ReminderErrorCode.VAL_STATE_CONFLICT);
        }
        if (!repository.ignore(
                userId, reminderId, now)) {
            throw new BusinessException(
                    ReminderErrorCode.VAL_STATE_CONFLICT);
        }
    }

    private long currentUserId() {
        return currentUserProvider
                .currentUser()
                .userId();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(reminderClock);
    }
}
