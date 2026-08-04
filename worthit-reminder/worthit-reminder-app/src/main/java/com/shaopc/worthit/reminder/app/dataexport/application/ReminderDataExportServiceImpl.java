package com.shaopc.worthit.reminder.app.dataexport.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.web.error.CommonWebErrorCode;
import com.shaopc.worthit.reminder.client.response.ReminderDataExportResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

/** 在 Reminder 本地可重复读快照中生成全状态历史分片。 */
@Service
public class ReminderDataExportServiceImpl
        implements ReminderDataExportService {

    private final ReminderDataExportQuery query;
    private final ObjectMapper objectMapper;
    private final Clock reminderClock;
    private final ReminderDataExportProperties properties;

    /** 创建 Reminder 数据导出用例。 */
    public ReminderDataExportServiceImpl(
            ReminderDataExportQuery query,
            ObjectMapper objectMapper,
            Clock reminderClock,
            ReminderDataExportProperties properties) {
        this.query = Objects.requireNonNull(query, "导出查询不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "JSON序列化器不能为空");
        this.reminderClock = Objects.requireNonNull(reminderClock, "业务时钟不能为空");
        this.properties = Objects.requireNonNull(properties, "导出配置不能为空");
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ReminderDataExportResponse exportUserData(long userId) {
        if (userId <= 0) {
            throw new BusinessException(
                    CommonWebErrorCode.VAL_INVALID_ARGUMENT, "用户标识必须为正整数");
        }
        RemainingRecords remaining = new RemainingRecords(properties.maxRecords());
        List<ReminderDataExportResponse.Binding> bindings =
                remaining.take(query.bindings(userId, remaining.probeLimit()));
        List<ReminderDataExportResponse.Instance> instances =
                remaining.take(query.instances(userId, remaining.probeLimit()));

        ReminderDataExportResponse response = new ReminderDataExportResponse(
                1,
                reminderClock.instant(),
                reminderClock.getZone().getId(),
                Long.toString(userId),
                bindings,
                instances);
        enforceSerializedSize(response);
        return response;
    }

    private void enforceSerializedSize(ReminderDataExportResponse response) {
        try {
            if (objectMapper.writeValueAsBytes(response).length
                    > properties.maxFragmentBytes()) {
                throw limitExceeded();
            }
        } catch (JsonProcessingException exception) {
            throw new BusinessException(
                    CommonWebErrorCode.SYS_ERROR,
                    "Reminder导出分片序列化失败",
                    exception);
        }
    }

    private static BusinessException limitExceeded() {
        return new BusinessException(CommonWebErrorCode.DATA_EXPORT_LIMIT_EXCEEDED);
    }

    private static final class RemainingRecords {
        private int value;

        private RemainingRecords(int value) {
            this.value = value;
        }

        private int probeLimit() {
            return value + 1;
        }

        private <T> List<T> take(List<T> records) {
            List<T> required = List.copyOf(records);
            if (required.size() > value) {
                throw limitExceeded();
            }
            value -= required.size();
            return required;
        }
    }
}
