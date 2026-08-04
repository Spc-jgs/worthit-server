package com.shaopc.worthit.reminder.app.dataexport.infrastructure.persistence;

import com.shaopc.worthit.reminder.app.dataexport.application.ReminderDataExportQuery;
import com.shaopc.worthit.reminder.client.response.ReminderDataExportResponse;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 将 Reminder 导出行映射为公开 Client 模型。
 */
@Repository
public class MybatisReminderDataExportQuery implements ReminderDataExportQuery {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final ReminderDataExportMapper mapper;

    public MybatisReminderDataExportQuery(ReminderDataExportMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<ReminderDataExportResponse.Binding> bindings(long userId, int limit) {
        return mapper.selectBindings(userId, limit).stream()
                .map(row -> new ReminderDataExportResponse.Binding(
                        id(row.getId()), row.getBusinessType(), id(row.getBusinessId()),
                        row.getReminderType(), enabled(row.getReminderEnabled()),
                        instant(row.getCreateTime()), instant(row.getUpdateTime())))
                .toList();
    }

    @Override
    public List<ReminderDataExportResponse.Instance> instances(long userId, int limit) {
        return mapper.selectInstances(userId, limit).stream()
                .map(row -> new ReminderDataExportResponse.Instance(
                        id(row.getId()), id(row.getBindingId()), row.getBusinessDate(),
                        instant(row.getRemindAt()), row.getTimezone(), row.getStatus(),
                        instant(row.getResolvedAt()), row.getResolutionReason(),
                        instant(row.getCreateTime()), instant(row.getUpdateTime())))
                .toList();
    }

    private static String id(Long value) {
        return value == null ? null : Long.toString(value);
    }

    private static boolean enabled(Integer value) {
        return Integer.valueOf(1).equals(value);
    }

    private static Instant instant(LocalDateTime value) {
        return value == null ? null : value.atZone(BUSINESS_ZONE).toInstant();
    }
}
