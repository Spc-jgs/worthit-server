package com.shaopc.worthit.tracking.dataexport.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.web.error.CommonWebErrorCode;
import com.shaopc.worthit.tracking.client.response.TrackingDataExportResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

/** 在 Tracking 本地可重复读快照中生成全有或全无的用户数据分片。 */
@Service
public class TrackingDataExportServiceImpl
        implements TrackingDataExportService {

    private static final int SCHEMA_VERSION = 1;

    private final TrackingDataExportQuery query;
    private final ObjectMapper objectMapper;
    private final Clock trackingClock;
    private final TrackingDataExportProperties properties;

    /** 创建 Tracking 数据导出用例。 */
    public TrackingDataExportServiceImpl(
            TrackingDataExportQuery query,
            ObjectMapper objectMapper,
            Clock trackingClock,
            TrackingDataExportProperties properties) {
        this.query = Objects.requireNonNull(query, "导出查询不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "JSON序列化器不能为空");
        this.trackingClock = Objects.requireNonNull(trackingClock, "业务时钟不能为空");
        this.properties = Objects.requireNonNull(properties, "导出配置不能为空");
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public TrackingDataExportResponse exportUserData(long userId) {
        if (userId <= 0) {
            throw new BusinessException(
                    CommonWebErrorCode.VAL_INVALID_ARGUMENT, "用户标识必须为正整数");
        }

        RemainingRecords remaining = new RemainingRecords(properties.maxRecords());
        List<TrackingDataExportResponse.Category> categories =
                remaining.take(query.categories(userId, remaining.probeLimit()));
        List<TrackingDataExportResponse.Item> items =
                remaining.take(query.items(userId, remaining.probeLimit()));
        List<TrackingDataExportResponse.Subscription> subscriptions =
                remaining.take(query.subscriptions(userId, remaining.probeLimit()));
        List<TrackingDataExportResponse.Wish> wishes =
                remaining.take(query.wishes(userId, remaining.probeLimit()));
        List<TrackingDataExportResponse.Disposal> disposals =
                remaining.take(query.disposals(userId, remaining.probeLimit()));
        List<TrackingDataExportResponse.Replacement> replacements =
                remaining.take(query.replacements(userId, remaining.probeLimit()));

        TrackingDataExportResponse response = new TrackingDataExportResponse(
                SCHEMA_VERSION,
                trackingClock.instant(),
                trackingClock.getZone().getId(),
                Long.toString(userId),
                categories,
                items,
                subscriptions,
                wishes,
                disposals,
                replacements);
        enforceSerializedSize(response);
        return response;
    }

    private void enforceSerializedSize(TrackingDataExportResponse response) {
        try {
            if (objectMapper.writeValueAsBytes(response).length
                    > properties.maxFragmentBytes()) {
                throw limitExceeded();
            }
        } catch (JsonProcessingException exception) {
            throw new BusinessException(
                    CommonWebErrorCode.SYS_ERROR,
                    "Tracking导出分片序列化失败",
                    exception);
        }
    }

    private static BusinessException limitExceeded() {
        return new BusinessException(
                CommonWebErrorCode.DATA_EXPORT_LIMIT_EXCEEDED);
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
