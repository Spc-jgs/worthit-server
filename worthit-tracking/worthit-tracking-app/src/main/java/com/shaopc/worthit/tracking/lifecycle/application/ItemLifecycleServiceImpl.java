package com.shaopc.worthit.tracking.lifecycle.application;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.core.pagination.PageQuery;
import com.shaopc.worthit.common.core.pagination.PageResult;
import com.shaopc.worthit.common.web.error.CommonWebErrorCode;
import com.shaopc.worthit.reminder.client.command.ReconcileReminderCommand;
import com.shaopc.worthit.reminder.client.contract.ReminderClientContract;
import com.shaopc.worthit.reminder.client.model.ReminderBusinessType;
import com.shaopc.worthit.reminder.client.model.ReminderOperationType;
import com.shaopc.worthit.reminder.client.model.ReminderType;
import com.shaopc.worthit.tracking.idempotency.application.IdempotencyExecutionCoordinator;
import com.shaopc.worthit.tracking.idempotency.application.RequestDigest;
import com.shaopc.worthit.tracking.idempotency.application.TrackingOperation;
import com.shaopc.worthit.tracking.item.domain.Item;
import com.shaopc.worthit.tracking.item.domain.ItemDeletionState;
import com.shaopc.worthit.tracking.item.domain.ItemErrorCode;
import com.shaopc.worthit.tracking.item.domain.ItemRepository;
import com.shaopc.worthit.tracking.item.domain.ItemWithCategory;
import com.shaopc.worthit.tracking.lifecycle.domain.DisposalType;
import com.shaopc.worthit.tracking.lifecycle.domain.ItemDisposal;
import com.shaopc.worthit.tracking.lifecycle.domain.ItemDisposalRepository;
import com.shaopc.worthit.tracking.lifecycle.domain.ItemLifecycleStateMachine;
import com.shaopc.worthit.tracking.lifecycle.domain.ItemReplacement;
import com.shaopc.worthit.tracking.lifecycle.domain.ItemReplacementRepository;
import com.shaopc.worthit.tracking.outbox.application.ReminderOutboxWriter;
import com.shaopc.worthit.tracking.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 编排物品处置状态机、幂等、本地事务与提醒 Outbox。
 */
@Service
@RequiredArgsConstructor
public class ItemLifecycleServiceImpl
        implements ItemLifecycleService {

    private static final int MAX_REMARK_LENGTH = 512;
    private static final BigDecimal MAX_MONEY =
            new BigDecimal("999999999999.999999");

    private final ItemRepository itemRepository;
    private final ItemDisposalRepository disposalRepository;
    private final ItemReplacementRepository replacementRepository;
    private final LifecycleReviewQuery lifecycleReviewQuery;
    private final IdempotencyExecutionCoordinator
            idempotencyCoordinator;
    private final RequestDigest requestDigest;
    private final ReminderOutboxWriter outboxWriter;
    private final CurrentUserProvider currentUserProvider;
    private final Clock trackingClock;

    @Override
    public ItemLifecycleResult returnItem(
            long itemId,
            String idempotencyKey,
            ReturnItemCommand command) {
        ReturnItemCommand normalized = normalize(command);
        LocalDate today = LocalDate.now(trackingClock);
        validate(normalized.version(), normalized.returnDate(),
                normalized.remark(), today);
        long userId = currentUserProvider
                .currentUser()
                .userId();
        DisposeDigest digest = new DisposeDigest(
                itemId,
                normalized.version(),
                normalized.returnDate(),
                null,
                normalized.remark());
        return idempotencyCoordinator.execute(
                userId,
                TrackingOperation.ITEM_RETURN,
                idempotencyKey,
                requestDigest.hash(digest),
                ItemLifecycleResult.class,
                () -> dispose(
                        userId,
                        itemId,
                        normalized.version(),
                        DisposalType.RETURNED,
                        normalized.returnDate(),
                        null,
                        normalized.remark(),
                        today));
    }

    @Override
    public ItemLifecycleResult sellItem(
            long itemId,
            String idempotencyKey,
            SellItemCommand command) {
        SellItemCommand normalized = normalize(command);
        LocalDate today = LocalDate.now(trackingClock);
        validate(
                normalized.version(),
                normalized.saleDate(),
                normalized.remark(),
                today);
        validateSaleAmount(normalized.saleAmount());
        long userId = currentUserProvider
                .currentUser()
                .userId();
        DisposeDigest digest = new DisposeDigest(
                itemId,
                normalized.version(),
                normalized.saleDate(),
                normalized.saleAmount(),
                normalized.remark());
        return idempotencyCoordinator.execute(
                userId,
                TrackingOperation.ITEM_SELL,
                idempotencyKey,
                requestDigest.hash(digest),
                ItemLifecycleResult.class,
                () -> dispose(
                        userId,
                        itemId,
                        normalized.version(),
                        DisposalType.SOLD,
                        normalized.saleDate(),
                        normalized.saleAmount(),
                        normalized.remark(),
                        today));
    }

    @Override
    public ItemLifecycleResult scrapItem(
            long itemId,
            String idempotencyKey,
            ScrapItemCommand command) {
        ScrapItemCommand normalized = normalize(command);
        LocalDate today = LocalDate.now(trackingClock);
        validate(normalized.version(), normalized.scrapDate(),
                normalized.remark(), today);
        long userId = currentUserProvider
                .currentUser()
                .userId();
        DisposeDigest digest = new DisposeDigest(
                itemId,
                normalized.version(),
                normalized.scrapDate(),
                null,
                normalized.remark());
        return idempotencyCoordinator.execute(
                userId,
                TrackingOperation.ITEM_SCRAP,
                idempotencyKey,
                requestDigest.hash(digest),
                ItemLifecycleResult.class,
                () -> dispose(
                        userId,
                        itemId,
                        normalized.version(),
                        DisposalType.SCRAPPED,
                        normalized.scrapDate(),
                        null,
                        normalized.remark(),
                        today));
    }

    @Override
    public ItemReplacementResult replaceItem(
            long oldItemId,
            String idempotencyKey,
            ReplaceItemCommand command) {
        if (command == null
                || oldItemId <= 0
                || command.newItemId() <= 0
                || oldItemId == command.newItemId()) {
            throw invalid();
        }
        long userId = currentUserProvider
                .currentUser()
                .userId();
        ReplaceDigest digest = new ReplaceDigest(
                oldItemId, command.newItemId());
        return idempotencyCoordinator.execute(
                userId,
                TrackingOperation.ITEM_REPLACE,
                idempotencyKey,
                requestDigest.hash(digest),
                ItemReplacementResult.class,
                () -> replace(
                        userId,
                        oldItemId,
                        command.newItemId()));
    }

    @Override
    public PageResult<LifecycleReviewEntry> review(
            int page, int size) {
        long userId = currentUserProvider
                .currentUser()
                .userId();
        return lifecycleReviewQuery.findPage(
                userId, new PageQuery(page, size));
    }

    private ItemReplacementResult replace(
            long userId,
            long oldItemId,
            long newItemId) {
        List<Long> ids = List.of(oldItemId, newItemId)
                .stream()
                .sorted()
                .toList();
        List<ItemDeletionState> states =
                itemRepository.lockDeletionStates(ids, userId);
        if (states.size() != 2
                || states.stream().anyMatch(
                        ItemDeletionState::deleted)) {
            throw new BusinessException(
                    CommonWebErrorCode.RES_NOT_FOUND);
        }
        Item oldItem = itemById(states, oldItemId);
        Item newItem = itemById(states, newItemId);
        LocalDateTime now =
                LocalDateTime.now(trackingClock);
        ItemReplacement replacement =
                replacementRepository.save(
                        new ItemReplacement(
                                IdWorker.getId(),
                                userId,
                                oldItemId,
                                newItemId,
                                now));
        return new ItemReplacementResult(
                replacement.id(),
                new LifecycleItemBrief(
                        oldItem.id(), oldItem.name()),
                new LifecycleItemBrief(
                        newItem.id(), newItem.name()),
                replacement.createTime());
    }

    private static Item itemById(
            List<ItemDeletionState> states,
            long itemId) {
        return states.stream()
                .map(ItemDeletionState::item)
                .filter(item -> item.id() == itemId)
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        CommonWebErrorCode.RES_NOT_FOUND));
    }

    private ItemLifecycleResult dispose(
            long userId,
            long itemId,
            long expectedVersion,
            DisposalType type,
            LocalDate disposalDate,
            java.math.BigDecimal saleAmount,
            String remark,
            LocalDate today) {
        Item item = itemRepository.findByIdAndUserId(
                        itemId, userId)
                .map(ItemWithCategory::item)
                .orElseThrow(() -> new BusinessException(
                        CommonWebErrorCode.RES_NOT_FOUND));
        if (item.version() != expectedVersion) {
            throw stateConflict();
        }
        LocalDateTime now =
                LocalDateTime.now(trackingClock);
        ItemDisposal disposal =
                ItemLifecycleStateMachine.dispose(
                        IdWorker.getId(),
                        item,
                        type,
                        disposalDate,
                        saleAmount,
                        remark,
                        today,
                        now);
        if (!itemRepository.dispose(
                itemId,
                userId,
                expectedVersion,
                type.targetStatus(),
                now)) {
            throw transitionFailure(itemId, userId);
        }
        ItemDisposal saved =
                disposalRepository.save(disposal);
        writeDisposedWarrantyExpectation(
                item, type);
        return new ItemLifecycleResult(
                item.id(),
                type.targetStatus().code(),
                ItemDisposalDetail.from(saved),
                item.version() + 1,
                now);
    }

    private void writeDisposedWarrantyExpectation(
            Item item,
            DisposalType type) {
        LocalDate businessDate =
                item.warrantyExpireDate();
        LocalDateTime remindAt = businessDate == null
                ? null
                : businessDate.minusDays(7).atStartOfDay();
        outboxWriter.write(new ReconcileReminderCommand(
                item.userId(),
                ReminderBusinessType.ITEM,
                item.id(),
                ReminderType.WARRANTY,
                item.version() + 1,
                businessDate,
                remindAt,
                false,
                type.targetStatus().code(),
                ReminderOperationType.DISPOSE_ITEM,
                ReminderClientContract.SCHEMA_VERSION));
    }

    private BusinessException transitionFailure(
            long itemId, long userId) {
        if (itemRepository.findByIdAndUserId(
                itemId, userId).isEmpty()) {
            return new BusinessException(
                    CommonWebErrorCode.RES_NOT_FOUND);
        }
        return stateConflict();
    }

    private static ReturnItemCommand normalize(
            ReturnItemCommand command) {
        if (command == null) {
            throw invalid();
        }
        return new ReturnItemCommand(
                command.version(),
                command.returnDate(),
                normalizeRemark(command.remark()));
    }

    private static SellItemCommand normalize(
            SellItemCommand command) {
        if (command == null) {
            throw invalid();
        }
        BigDecimal amount = command.saleAmount();
        validateSaleAmount(amount);
        return new SellItemCommand(
                command.version(),
                command.saleDate(),
                amount.setScale(
                        6, RoundingMode.UNNECESSARY),
                normalizeRemark(command.remark()));
    }

    private static ScrapItemCommand normalize(
            ScrapItemCommand command) {
        if (command == null) {
            throw invalid();
        }
        return new ScrapItemCommand(
                command.version(),
                command.scrapDate(),
                normalizeRemark(command.remark()));
    }

    private static void validate(
            long version,
            LocalDate disposalDate,
            String remark,
            LocalDate today) {
        if (version <= 0
                || disposalDate == null
                || disposalDate.isAfter(today)
                || remark != null
                && remark.length() > MAX_REMARK_LENGTH) {
            throw invalid();
        }
    }

    private static String normalizeRemark(String remark) {
        if (remark == null) {
            return null;
        }
        String normalized = remark.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static void validateSaleAmount(
            BigDecimal amount) {
        if (amount == null
                || amount.signum() < 0
                || amount.scale() > 6
                || amount.compareTo(MAX_MONEY) > 0) {
            throw invalid();
        }
    }

    private static BusinessException invalid() {
        return new BusinessException(
                CommonWebErrorCode.VAL_INVALID_ARGUMENT);
    }

    private static BusinessException stateConflict() {
        return new BusinessException(
                ItemErrorCode.VAL_STATE_CONFLICT);
    }

    private record DisposeDigest(
            long itemId,
            long version,
            LocalDate disposalDate,
            java.math.BigDecimal saleAmount,
            String remark) {
    }

    private record ReplaceDigest(
            long oldItemId,
            long newItemId) {
    }
}
