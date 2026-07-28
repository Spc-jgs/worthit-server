package com.shaopc.worthit.reminder.app.reminder.infrastructure.persistence;

import com.shaopc.worthit.common.core.pagination.PageQuery;
import com.shaopc.worthit.common.core.pagination.PageResult;
import com.shaopc.worthit.reminder.app.reminder.application.ReminderInstanceState;
import com.shaopc.worthit.reminder.app.reminder.application.ReminderListItem;
import com.shaopc.worthit.reminder.app.reminder.application.ReminderTab;
import com.shaopc.worthit.reminder.app.reminder.application.ReminderViewRepository;
import com.shaopc.worthit.reminder.client.model.ReminderBusinessType;
import com.shaopc.worthit.reminder.client.model.ReminderType;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 基于 MySQL 的 Reminder 公网查询与忽略仓储。
 */
@Repository
public class MybatisReminderViewRepository
        implements ReminderViewRepository {

    private final ReminderViewMapper mapper;

    /**
     * 创建 Reminder 公网仓储。
     */
    public MybatisReminderViewRepository(
            ReminderViewMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public PageResult<ReminderListItem> list(
            long userId,
            ReminderTab tab,
            LocalDateTime now,
            PageQuery pageQuery) {
        long offset = (long) (pageQuery.page() - 1)
                * pageQuery.size();
        List<ReminderListRowDO> rows;
        long total;
        if (tab == ReminderTab.PENDING) {
            rows = mapper.selectPendingPage(
                    userId,
                    now,
                    offset,
                    pageQuery.size());
            total = mapper.countPending(userId, now);
        } else {
            rows = mapper.selectDonePage(
                    userId, offset, pageQuery.size());
            total = mapper.countDone(userId);
        }
        return PageResult.of(
                rows.stream().map(this::toItem).toList(),
                pageQuery,
                total);
    }

    @Override
    public long countPending(
            long userId,
            LocalDateTime now) {
        return mapper.countPending(userId, now);
    }

    @Override
    public Optional<ReminderInstanceState> findByIdForUpdate(
            long userId,
            long reminderId) {
        return Optional.ofNullable(
                        mapper.selectByIdForUpdate(
                                userId, reminderId))
                .map(row -> new ReminderInstanceState(
                        row.getId(),
                        row.getStatus(),
                        row.getRemindAt()));
    }

    @Override
    public boolean ignore(
            long userId,
            long reminderId,
            LocalDateTime now) {
        return mapper.ignore(
                userId, reminderId, now) == 1;
    }

    private ReminderListItem toItem(
            ReminderListRowDO row) {
        return new ReminderListItem(
                row.getId(),
                ReminderType.valueOf(
                        row.getReminderType()),
                ReminderBusinessType.valueOf(
                        row.getBusinessType()),
                row.getBusinessId(),
                row.getBusinessDate(),
                row.getRemindAt(),
                row.getStatus());
    }
}
