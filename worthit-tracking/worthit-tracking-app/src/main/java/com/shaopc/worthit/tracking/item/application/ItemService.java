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
import com.shaopc.worthit.tracking.category.domain.Category;
import com.shaopc.worthit.tracking.category.domain.CategoryRepository;
import com.shaopc.worthit.tracking.item.domain.Item;
import com.shaopc.worthit.tracking.item.domain.ItemCost;
import com.shaopc.worthit.tracking.item.domain.ItemCostCalculator;
import com.shaopc.worthit.tracking.item.domain.ItemErrorCode;
import com.shaopc.worthit.tracking.item.domain.ItemRepository;
import com.shaopc.worthit.tracking.item.domain.ItemWithCategory;
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

/**
 * 编排 Item 新建与查询用例。
 */
@Service
@RequiredArgsConstructor
public class ItemService {

    private final ItemRepository itemRepository;
    private final CategoryRepository categoryRepository;
    private final ItemIdempotencyStore idempotencyStore;
    private final ItemRequestDigest requestDigest;
    private final ItemOutboxWriter outboxWriter;
    private final CurrentUserProvider currentUserProvider;
    private final Clock trackingClock;

    /**
     * 幂等新建物品。
     */
    @Transactional
    public ItemDetail create(
            String idempotencyKey,
            CreateItemCommand command) {
        long userId = currentUserId();
        LocalDate today = LocalDate.now(trackingClock);
        CreateItemCommand normalized = normalize(command);
        validate(normalized, today);

        String requestHash = requestDigest.hash(normalized);
        ItemIdempotencyClaim claim = idempotencyStore.claim(
                userId, idempotencyKey, requestHash);
        if (claim.status()
                == ItemIdempotencyClaim.Status.CONFLICT) {
            throw new BusinessException(
                    ItemErrorCode.IDEM_CONFLICT);
        }
        if (claim.status()
                == ItemIdempotencyClaim.Status.REPLAY) {
            return claim.replay();
        }

        Category category = resolveCategory(
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
                Item.HOLDING,
                1,
                now,
                now));

        writeWarrantyExpectationIfNeeded(
                saved, today);
        ItemDetail detail = toDetail(
                new ItemWithCategory(saved, category.name()),
                today);
        idempotencyStore.complete(
                userId, idempotencyKey, requestHash, detail);
        return detail;
    }

    /**
     * 查询当前用户物品详情。
     */
    @Transactional(readOnly = true)
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

    private Category resolveCategory(
            Long categoryId, long userId) {
        if (categoryId == null) {
            return categoryRepository
                    .getOrCreateUncategorized(userId);
        }
        return categoryRepository.findByIdAndUserId(
                        categoryId, userId)
                .orElseThrow(() -> new BusinessException(
                        CommonWebErrorCode.RES_NOT_FOUND));
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
        outboxWriter.write(new ReconcileReminderCommand(
                item.userId(),
                ReminderBusinessType.ITEM,
                item.id(),
                ReminderType.WARRANTY,
                item.version(),
                item.warrantyExpireDate(),
                remindAt,
                true,
                item.lifecycleStatus(),
                ReminderOperationType.INITIAL_SYNC,
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
                item.lifecycleStatus(),
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
                item.lifecycleStatus(),
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

    private long currentUserId() {
        return currentUserProvider.currentUser().userId();
    }
}
