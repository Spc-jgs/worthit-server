package com.shaopc.worthit.tracking.item.application;

import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.core.pagination.PageQuery;
import com.shaopc.worthit.common.core.pagination.PageResult;
import com.shaopc.worthit.common.web.error.CommonWebErrorCode;
import com.shaopc.worthit.reminder.client.command.ReconcileReminderCommand;
import com.shaopc.worthit.reminder.client.contract.ReminderClientContract;
import com.shaopc.worthit.reminder.client.model.ReminderBusinessType;
import com.shaopc.worthit.reminder.client.model.ReminderOperationType;
import com.shaopc.worthit.reminder.client.model.ReminderType;
import com.shaopc.worthit.tracking.category.application.CategoryReferenceResolver;
import com.shaopc.worthit.tracking.category.domain.Category;
import com.shaopc.worthit.tracking.idempotency.application.IdempotencyClaim;
import com.shaopc.worthit.tracking.idempotency.application.IdempotencyStore;
import com.shaopc.worthit.tracking.idempotency.application.RequestDigest;
import com.shaopc.worthit.tracking.idempotency.application.RestoreTokenClaim;
import com.shaopc.worthit.tracking.idempotency.application.RestoreTokenStore;
import com.shaopc.worthit.tracking.idempotency.application.TrackingOperation;
import com.shaopc.worthit.tracking.item.domain.Item;
import com.shaopc.worthit.tracking.item.domain.ItemCost;
import com.shaopc.worthit.tracking.item.domain.ItemCostCalculator;
import com.shaopc.worthit.tracking.item.domain.ItemDeletionState;
import com.shaopc.worthit.tracking.item.domain.ItemErrorCode;
import com.shaopc.worthit.tracking.item.domain.ItemLifecycleStatus;
import com.shaopc.worthit.tracking.item.domain.ItemRepository;
import com.shaopc.worthit.tracking.item.domain.ItemWithCategory;
import com.shaopc.worthit.tracking.outbox.application.ReminderOutboxWriter;
import com.shaopc.worthit.tracking.restore.application.RestoreClaimCoordinator;
import com.shaopc.worthit.tracking.restore.application.RestoreWindowPolicy;
import com.shaopc.worthit.tracking.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 编排 Item 创建、查询、更新、删除与短时恢复用例。
 */
@Service
@RequiredArgsConstructor
public class ItemServiceImpl implements ItemService {

    private final ItemRepository itemRepository;
    private final CategoryReferenceResolver categoryReferenceResolver;
    private final IdempotencyStore idempotencyStore;
    private final RequestDigest requestDigest;
    private final ReminderOutboxWriter outboxWriter;
    private final RestoreTokenStore restoreTokenStore;
    private final CurrentUserProvider currentUserProvider;
    private final Clock trackingClock;
    private final RestoreWindowPolicy restoreWindowPolicy;
    private final RestoreClaimCoordinator restoreClaimCoordinator;

    /**
     * 幂等新建物品。
     */
    @Transactional
    @Override
    public ItemDetail create(
            String idempotencyKey,
            CreateItemCommand command) {
        long userId = currentUserId();
        LocalDate today = LocalDate.now(trackingClock);
        CreateItemCommand normalized = normalize(command);
        validate(normalized, today);

        String requestHash = requestDigest.hash(normalized);
        IdempotencyClaim<ItemDetail> claim =
                idempotencyStore.claim(
                        userId,
                        TrackingOperation.ITEM_CREATE,
                        idempotencyKey,
                        requestHash,
                        ItemDetail.class);
        if (claim.status()
                == IdempotencyClaim.Status.CONFLICT) {
            throw new BusinessException(
                    ItemErrorCode.IDEM_CONFLICT);
        }
        if (claim.status()
                == IdempotencyClaim.Status.REPLAY) {
            return claim.replay();
        }

        Category category = categoryReferenceResolver.resolve(
                normalized.categoryId(), userId);
        LocalDateTime now = LocalDateTime.now(trackingClock);
        boolean reminderEnabled = reminderEnabled(normalized);
        Item saved = itemRepository.create(new Item(
                0,
                userId,
                category.id(),
                normalized.name(),
                normalized.purchasePrice(),
                normalized.expectedYears(),
                normalized.residualValue(),
                normalized.purchaseDate(),
                normalized.warrantyExpireDate(),
                reminderEnabled,
                normalized.brandModel(),
                normalized.remark(),
                ItemLifecycleStatus.HOLDING,
                1,
                now,
                now));

        writeWarrantyExpectationIfNeeded(
                saved, today);
        ItemDetail detail = toDetail(
                new ItemWithCategory(saved, category.name()),
                today);
        idempotencyStore.complete(
                userId,
                TrackingOperation.ITEM_CREATE,
                idempotencyKey,
                requestHash,
                detail);
        return detail;
    }

    /**
     * 查询当前用户物品详情。
     */
    @Transactional(readOnly = true)
    @Override
    public ItemDetail detail(long itemId) {
        LocalDate today = LocalDate.now(trackingClock);
        return itemRepository.findByIdAndUserId(
                        itemId, currentUserId())
                .map(item -> toDetail(item, today))
                .orElseThrow(() -> new BusinessException(
                        CommonWebErrorCode.RES_NOT_FOUND));
    }

    /**
     * 分页查询当前用户物品。
     */
    @Transactional(readOnly = true)
    @Override
    public PageResult<ItemSummary> list(
            int page,
            int size,
            String keyword,
            Long categoryId) {
        PageQuery pageQuery = new PageQuery(page, size);
        LocalDate today = LocalDate.now(trackingClock);
        PageResult<ItemWithCategory> result =
                itemRepository.findPage(
                        currentUserId(),
                        pageQuery,
                        normalizeNullableText(keyword),
                        categoryId);
        List<ItemSummary> items = result.getItems()
                .stream()
                .map(item -> toSummary(item, today))
                .toList();
        return PageResult.of(items, pageQuery, result.getTotal());
    }

    /**
     * 按乐观锁版本更新当前用户物品。
     */
    @Transactional
    @Override
    public ItemDetail update(
            long itemId,
            String idempotencyKey,
            UpdateItemCommand command) {
        long userId = currentUserId();
        LocalDate today = LocalDate.now(trackingClock);
        UpdateItemCommand normalized = normalize(command);
        validate(normalized, today);
        String requestHash = requestDigest.hash(normalized);
        IdempotencyClaim<ItemDetail> claim =
                idempotencyStore.claim(
                        userId,
                        TrackingOperation.ITEM_UPDATE,
                        idempotencyKey,
                        requestHash,
                        ItemDetail.class);
        if (claim.status()
                == IdempotencyClaim.Status.CONFLICT) {
            throw new BusinessException(
                    ItemErrorCode.IDEM_CONFLICT);
        }
        if (claim.status()
                == IdempotencyClaim.Status.REPLAY) {
            return claim.replay();
        }
        Item existing = itemRepository.findByIdAndUserId(
                        itemId, userId)
                .map(ItemWithCategory::item)
                .orElseThrow(() -> new BusinessException(
                        CommonWebErrorCode.RES_NOT_FOUND));
        if (existing.version() != normalized.version()) {
            throw stateConflict();
        }

        Category category = categoryReferenceResolver.resolve(
                normalized.categoryId(), userId);
        LocalDateTime now = LocalDateTime.now(trackingClock);
        Item updated = new Item(
                existing.id(),
                existing.userId(),
                category.id(),
                normalized.name(),
                normalized.purchasePrice(),
                normalized.expectedYears(),
                normalized.residualValue(),
                normalized.purchaseDate(),
                normalized.warrantyExpireDate(),
                normalized.warrantyReminderEnabled(),
                normalized.brandModel(),
                normalized.remark(),
                existing.lifecycleStatus(),
                existing.version() + 1,
                existing.createTime(),
                now);
        if (!itemRepository.update(
                updated, normalized.version())) {
            throw stateConflict();
        }
        writeUpdatedWarrantyExpectation(existing, updated);
        ItemDetail detail = itemRepository.findByIdAndUserId(
                        itemId, userId)
                .map(item -> toDetail(item, today))
                .orElseThrow(() -> new IllegalStateException(
                        "Item更新后无法读取"));
        idempotencyStore.complete(
                userId,
                TrackingOperation.ITEM_UPDATE,
                idempotencyKey,
                requestHash,
                detail);
        return detail;
    }

    /**
     * 逻辑删除当前用户物品并签发短时恢复令牌。
     */
    @Transactional
    @Override
    public DeleteItemResult delete(
            long itemId,
            long version,
            String idempotencyKey) {
        long userId = currentUserId();
        DeleteItemCommand command =
                new DeleteItemCommand(itemId, version);
        String requestHash = requestDigest.hash(command);
        IdempotencyClaim<DeleteItemResult> claim =
                idempotencyStore.claim(
                        userId,
                        TrackingOperation.ITEM_DELETE,
                        idempotencyKey,
                        requestHash,
                        DeleteItemResult.class);
        if (claim.status()
                == IdempotencyClaim.Status.CONFLICT) {
            throw new BusinessException(
                    ItemErrorCode.IDEM_CONFLICT);
        }
        if (claim.status()
                == IdempotencyClaim.Status.REPLAY) {
            return claim.replay();
        }
        Item existing = itemRepository.findByIdAndUserId(
                        itemId, userId)
                .map(ItemWithCategory::item)
                .orElseThrow(() -> new BusinessException(
                        CommonWebErrorCode.RES_NOT_FOUND));
        if (existing.version() != version) {
            throw stateConflict();
        }
        LocalDateTime now = LocalDateTime.now(trackingClock);
        if (!itemRepository.delete(
                itemId, userId, version, now)) {
            throw stateConflict();
        }

        long deletedVersion = version + 1;
        if (existing.warrantyReminderEnabled()
                && existing.warrantyExpireDate() != null) {
            writeWarrantyExpectation(
                    itemAtVersion(existing, deletedVersion, now),
                    false,
                    ReminderOperationType.DELETE_OBJECT);
        }
        LocalDateTime deadline =
                restoreWindowPolicy.deadlineFrom(now);
        String restoreToken = restoreTokenStore.issue(
                userId,
                TrackingOperation.ITEM_RESTORE,
                itemId,
                deletedVersion,
                deadline);
        DeleteItemResult result = new DeleteItemResult(
                itemId, deadline, restoreToken);
        idempotencyStore.complete(
                userId,
                TrackingOperation.ITEM_DELETE,
                idempotencyKey,
                requestHash,
                result);
        return result;
    }

    /**
     * 在删除窗口内恢复当前用户物品。
     */
    @Transactional
    @Override
    public ItemDetail restore(
            long itemId,
            long deletedVersion,
            String restoreToken) {
        long userId = currentUserId();
        ItemDeletionState state =
                itemRepository.findDeletionState(itemId, userId)
                        .orElseThrow(() -> new BusinessException(
                                CommonWebErrorCode.RES_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now(trackingClock);
        RestoreTokenClaim<ItemDetail> claim =
                restoreClaimCoordinator
                        .claimWithCategoryReservation(
                                userId,
                                state.item().categoryId(),
                                TrackingOperation.ITEM_RESTORE,
                                itemId,
                                deletedVersion,
                                restoreToken,
                                now,
                                ItemDetail.class);
        if (claim.status()
                == RestoreTokenClaim.Status.REPLAY) {
            return claim.replay();
        }
        if (claim.status()
                != RestoreTokenClaim.Status.AVAILABLE
                || !state.deleted()
                || state.item().version() != deletedVersion) {
            throw stateConflict();
        }
        if (!itemRepository.restore(
                itemId, userId, deletedVersion, now)) {
            throw stateConflict();
        }

        ItemDetail restored = itemRepository.findByIdAndUserId(
                        itemId, userId)
                .map(item -> toDetail(
                        item, LocalDate.now(trackingClock)))
                .orElseThrow(() -> new IllegalStateException(
                        "Item恢复后无法读取"));
        restoreTokenStore.complete(
                userId,
                TrackingOperation.ITEM_RESTORE,
                itemId,
                deletedVersion,
                restoreToken,
                restored);
        return restored;
    }

    private void writeWarrantyExpectationIfNeeded(
            Item item, LocalDate today) {
        if (!item.warrantyReminderEnabled()
                || item.warrantyExpireDate() == null) {
            return;
        }
        LocalDateTime remindAt = item.warrantyExpireDate()
                .minusDays(7)
                .atStartOfDay();
        if (remindAt.toLocalDate().isBefore(today)) {
            return;
        }
        writeWarrantyExpectation(
                item,
                true,
                ReminderOperationType.INITIAL_SYNC);
    }

    private void writeUpdatedWarrantyExpectation(
            Item previous, Item updated) {
        ReminderOperationType operationType;
        if (previous.warrantyReminderEnabled()
                && !updated.warrantyReminderEnabled()) {
            operationType =
                    ReminderOperationType.DISABLE_REMINDER;
        } else if (!previous.warrantyReminderEnabled()
                && updated.warrantyReminderEnabled()) {
            operationType =
                    ReminderOperationType.ENABLE_REMINDER;
        } else if (updated.warrantyReminderEnabled()
                && !Objects.equals(
                        previous.warrantyExpireDate(),
                        updated.warrantyExpireDate())) {
            operationType =
                    ReminderOperationType.UPDATE_BUSINESS_DATE;
        } else {
            return;
        }
        writeWarrantyExpectation(
                updated,
                updated.warrantyReminderEnabled(),
                operationType);
    }

    private void writeWarrantyExpectation(
            Item item,
            boolean reminderEnabled,
            ReminderOperationType operationType) {
        LocalDate businessDate = item.warrantyExpireDate();
        LocalDateTime remindAt = businessDate == null
                ? null
                : businessDate.minusDays(7).atStartOfDay();
        outboxWriter.write(new ReconcileReminderCommand(
                item.userId(),
                ReminderBusinessType.ITEM,
                item.id(),
                ReminderType.WARRANTY,
                item.version(),
                businessDate,
                remindAt,
                reminderEnabled,
                item.lifecycleStatus().code(),
                operationType,
                ReminderClientContract.SCHEMA_VERSION));
    }

    private ItemDetail toDetail(
            ItemWithCategory view, LocalDate today) {
        Item item = view.item();
        ItemCost cost = ItemCostCalculator.calculate(
                item.purchasePrice(),
                item.expectedYears(),
                item.residualValue(),
                item.purchaseDate(),
                today);
        return new ItemDetail(
                item.id(),
                item.name(),
                item.categoryId(),
                view.categoryName(),
                item.purchasePrice().toPlainString(),
                item.expectedYears().toPlainString(),
                plain(item.residualValue()),
                cost.residualUnset(),
                item.purchaseDate(),
                item.warrantyExpireDate(),
                item.warrantyReminderEnabled(),
                item.brandModel(),
                item.remark(),
                item.lifecycleStatus().code(),
                cost.expectedUseDays(),
                cost.planDailyCost().toPlainString(),
                cost.planDailyCostDisplay(),
                cost.planDailyCostTiny(),
                cost.holdingDays(),
                plain(cost.holdingDailyCost()),
                cost.holdingDailyCostDisplay(),
                item.version(),
                item.createTime(),
                item.updateTime());
    }

    private ItemSummary toSummary(
            ItemWithCategory view, LocalDate today) {
        Item item = view.item();
        ItemCost cost = ItemCostCalculator.calculate(
                item.purchasePrice(),
                item.expectedYears(),
                item.residualValue(),
                item.purchaseDate(),
                today);
        return new ItemSummary(
                item.id(),
                item.name(),
                view.categoryName(),
                cost.planDailyCostDisplay(),
                cost.residualUnset(),
                item.lifecycleStatus().code(),
                item.createTime());
    }

    private static CreateItemCommand normalize(
            CreateItemCommand command) {
        return new CreateItemCommand(
                command.name().trim(),
                command.categoryId(),
                command.purchasePrice().setScale(
                        6, RoundingMode.UNNECESSARY),
                command.expectedYears().setScale(
                        3, RoundingMode.UNNECESSARY),
                command.residualValue() == null
                        ? null
                        : command.residualValue().setScale(
                                6, RoundingMode.UNNECESSARY),
                command.purchaseDate(),
                command.warrantyExpireDate(),
                command.warrantyReminderEnabled(),
                normalizeNullableText(command.brandModel()),
                normalizeNullableText(command.remark()));
    }

    private static UpdateItemCommand normalize(
            UpdateItemCommand command) {
        return new UpdateItemCommand(
                command.version(),
                command.name().trim(),
                command.categoryId(),
                command.purchasePrice().setScale(
                        6, RoundingMode.UNNECESSARY),
                command.expectedYears().setScale(
                        3, RoundingMode.UNNECESSARY),
                command.residualValue() == null
                        ? null
                        : command.residualValue().setScale(
                                6, RoundingMode.UNNECESSARY),
                command.purchaseDate(),
                command.warrantyExpireDate(),
                command.warrantyReminderEnabled(),
                normalizeNullableText(command.brandModel()),
                normalizeNullableText(command.remark()));
    }

    private static void validate(
            CreateItemCommand command, LocalDate today) {
        if (command.purchasePrice().signum() < 0
                || command.expectedYears().signum() <= 0
                || command.residualValue() != null
                && command.residualValue().signum() < 0
                || command.purchaseDate() != null
                && command.purchaseDate().isAfter(today)
                || Boolean.TRUE.equals(
                        command.warrantyReminderEnabled())
                && command.warrantyExpireDate() == null) {
            throw new BusinessException(
                    CommonWebErrorCode.VAL_INVALID_ARGUMENT);
        }
    }

    private static void validate(
            UpdateItemCommand command, LocalDate today) {
        if (command.version() <= 0
                || command.purchasePrice().signum() < 0
                || command.expectedYears().signum() <= 0
                || command.residualValue() != null
                && command.residualValue().signum() < 0
                || command.purchaseDate() != null
                && command.purchaseDate().isAfter(today)
                || command.warrantyReminderEnabled()
                && command.warrantyExpireDate() == null) {
            throw new BusinessException(
                    CommonWebErrorCode.VAL_INVALID_ARGUMENT);
        }
    }

    private static boolean reminderEnabled(
            CreateItemCommand command) {
        if (command.warrantyReminderEnabled() != null) {
            return command.warrantyReminderEnabled();
        }
        return command.warrantyExpireDate() != null;
    }

    private static String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String plain(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private static Item itemAtVersion(
            Item item,
            long version,
            LocalDateTime updateTime) {
        return new Item(
                item.id(),
                item.userId(),
                item.categoryId(),
                item.name(),
                item.purchasePrice(),
                item.expectedYears(),
                item.residualValue(),
                item.purchaseDate(),
                item.warrantyExpireDate(),
                item.warrantyReminderEnabled(),
                item.brandModel(),
                item.remark(),
                item.lifecycleStatus(),
                version,
                item.createTime(),
                updateTime);
    }

    private static BusinessException stateConflict() {
        return new BusinessException(
                ItemErrorCode.VAL_STATE_CONFLICT);
    }

    private long currentUserId() {
        return currentUserProvider.currentUser().userId();
    }
}
