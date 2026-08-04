package com.shaopc.worthit.tracking.dataexport.infrastructure.persistence;

import com.shaopc.worthit.tracking.client.response.TrackingDataExportResponse;
import com.shaopc.worthit.tracking.dataexport.application.TrackingDataExportQuery;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 将导出专用 MyBatis 行映射为公开 Client 模型。
 */
@Repository
public class MybatisTrackingDataExportQuery implements TrackingDataExportQuery {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final TrackingDataExportMapper mapper;

    public MybatisTrackingDataExportQuery(TrackingDataExportMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<TrackingDataExportResponse.Category> categories(long userId, int limit) {
        return mapper.selectCategories(userId, limit).stream()
                .map(row -> new TrackingDataExportResponse.Category(
                        id(row.getId()), row.getName(), row.getSystemCode(),
                        row.getVersion(), instant(row.getCreateTime()),
                        instant(row.getUpdateTime()), deleted(row.getDelFlag()),
                        instant(row.getDeleteTime())))
                .toList();
    }

    @Override
    public List<TrackingDataExportResponse.Item> items(long userId, int limit) {
        return mapper.selectItems(userId, limit).stream()
                .map(row -> new TrackingDataExportResponse.Item(
                        id(row.getId()), id(row.getCategoryId()), row.getName(),
                        decimal(row.getPurchasePrice()), decimal(row.getExpectedYears()),
                        decimal(row.getResidualValue()), row.getPurchaseDate(),
                        row.getWarrantyExpireDate(), enabled(row.getWarrantyReminderEnabled()),
                        row.getBrandModel(), row.getRemark(), id(row.getSourceWishId()),
                        row.getLifecycleStatus(), row.getVersion(),
                        instant(row.getCreateTime()), instant(row.getUpdateTime()),
                        deleted(row.getDelFlag()), instant(row.getDeleteTime())))
                .toList();
    }

    @Override
    public List<TrackingDataExportResponse.Subscription> subscriptions(
            long userId, int limit) {
        return mapper.selectSubscriptions(userId, limit).stream()
                .map(row -> new TrackingDataExportResponse.Subscription(
                        id(row.getId()), id(row.getCategoryId()), row.getName(),
                        decimal(row.getAmount()), row.getCurrency(),
                        row.getBillingCycleType(), row.getBillingCycleValue(),
                        decimal(row.getCnyReferenceAmount()), row.getNextRenewalDate(),
                        row.getAutoRenew(), enabled(row.getRenewalReminderEnabled()),
                        row.getStatus(), row.getRemark(), row.getVersion(),
                        instant(row.getCreateTime()), instant(row.getUpdateTime()),
                        deleted(row.getDelFlag()), instant(row.getDeleteTime())))
                .toList();
    }

    @Override
    public List<TrackingDataExportResponse.Wish> wishes(long userId, int limit) {
        return mapper.selectWishes(userId, limit).stream()
                .map(row -> new TrackingDataExportResponse.Wish(
                        id(row.getId()), id(row.getCategoryId()), row.getName(),
                        decimal(row.getExpectedPrice()), decimal(row.getExpectedYears()),
                        decimal(row.getResidualValue()), row.getReason(), row.getRemark(),
                        row.getWatchDeadline(), enabled(row.getWatchReminderEnabled()),
                        row.getStatus(), row.getLastAbandonReason(),
                        instant(row.getLastAbandonAt()), id(row.getConvertedItemId()),
                        row.getVersion(), instant(row.getCreateTime()),
                        instant(row.getUpdateTime()), deleted(row.getDelFlag()),
                        instant(row.getDeleteTime())))
                .toList();
    }

    @Override
    public List<TrackingDataExportResponse.Disposal> disposals(long userId, int limit) {
        return mapper.selectDisposals(userId, limit).stream()
                .map(row -> new TrackingDataExportResponse.Disposal(
                        id(row.getId()), id(row.getItemId()), row.getDisposalType(),
                        row.getDisposalDate(), decimal(row.getPurchasePriceSnapshot()),
                        decimal(row.getSaleAmount()), row.getRemark(),
                        instant(row.getCreateTime()), instant(row.getUpdateTime())))
                .toList();
    }

    @Override
    public List<TrackingDataExportResponse.Replacement> replacements(
            long userId, int limit) {
        return mapper.selectReplacements(userId, limit).stream()
                .map(row -> new TrackingDataExportResponse.Replacement(
                        id(row.getId()), id(row.getOldItemId()), id(row.getNewItemId()),
                        instant(row.getCreateTime())))
                .toList();
    }

    private static String id(Long value) {
        return value == null ? null : Long.toString(value);
    }

    private static String decimal(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private static boolean enabled(Integer value) {
        return Integer.valueOf(1).equals(value);
    }

    private static boolean deleted(Integer value) {
        return Integer.valueOf(1).equals(value);
    }

    private static Instant instant(LocalDateTime value) {
        return value == null ? null : value.atZone(BUSINESS_ZONE).toInstant();
    }
}
