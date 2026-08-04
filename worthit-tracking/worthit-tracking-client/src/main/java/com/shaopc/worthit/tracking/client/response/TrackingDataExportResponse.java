package com.shaopc.worthit.tracking.client.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Tracking 按数据所有权生成的基础数据导出分片。
 */
public record TrackingDataExportResponse(
        int schemaVersion,
        Instant capturedAt,
        String timeZone,
        String userId,
        List<Category> categories,
        List<Item> items,
        List<Subscription> subscriptions,
        List<Wish> wishes,
        List<Disposal> disposals,
        List<Replacement> replacements) {

    public TrackingDataExportResponse {
        categories = List.copyOf(categories);
        items = List.copyOf(items);
        subscriptions = List.copyOf(subscriptions);
        wishes = List.copyOf(wishes);
        disposals = List.copyOf(disposals);
        replacements = List.copyOf(replacements);
    }

    /** 分类记录。 */
    public record Category(
            String id,
            String name,
            String systemCode,
            long version,
            Instant createdAt,
            Instant updatedAt,
            boolean deleted,
            Instant deletedAt) {
    }

    /** 物品记录。 */
    public record Item(
            String id,
            String categoryId,
            String name,
            String purchasePrice,
            String expectedYears,
            String residualValue,
            LocalDate purchaseDate,
            LocalDate warrantyExpireDate,
            boolean warrantyReminderEnabled,
            String brandModel,
            String remark,
            String sourceWishId,
            String lifecycleStatus,
            long version,
            Instant createdAt,
            Instant updatedAt,
            boolean deleted,
            Instant deletedAt) {
    }

    /** 订阅记录。 */
    public record Subscription(
            String id,
            String categoryId,
            String name,
            String amount,
            String currency,
            String billingCycleType,
            Integer billingCycleValue,
            String cnyReferenceAmount,
            LocalDate nextRenewalDate,
            String autoRenew,
            boolean renewalReminderEnabled,
            String status,
            String remark,
            long version,
            Instant createdAt,
            Instant updatedAt,
            boolean deleted,
            Instant deletedAt) {
    }

    /** 想买记录，不包含内部 conversionKey。 */
    public record Wish(
            String id,
            String categoryId,
            String name,
            String expectedPrice,
            String expectedYears,
            String residualValue,
            String reason,
            String remark,
            LocalDate watchDeadline,
            boolean watchReminderEnabled,
            String status,
            String lastAbandonReason,
            Instant lastAbandonAt,
            String convertedItemId,
            long version,
            Instant createdAt,
            Instant updatedAt,
            boolean deleted,
            Instant deletedAt) {
    }

    /** 物品处置记录。 */
    public record Disposal(
            String id,
            String itemId,
            String disposalType,
            LocalDate disposalDate,
            String purchasePriceSnapshot,
            String saleAmount,
            String remark,
            Instant createdAt,
            Instant updatedAt) {
    }

    /** 物品替换关系。 */
    public record Replacement(
            String id,
            String oldItemId,
            String newItemId,
            Instant createdAt) {
    }
}
