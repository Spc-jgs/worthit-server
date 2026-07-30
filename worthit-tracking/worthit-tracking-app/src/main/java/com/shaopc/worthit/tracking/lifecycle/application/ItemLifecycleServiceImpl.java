package com.shaopc.worthit.tracking.lifecycle.application;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.shaopc.worthit.common.core.error.BusinessException;
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
import com.shaopc.worthit.tracking.item.domain.ItemErrorCode;
import com.shaopc.worthit.tracking.item.domain.ItemRepository;
import com.shaopc.worthit.tracking.item.domain.ItemWithCategory;
import com.shaopc.worthit.tracking.lifecycle.domain.DisposalType;
import com.shaopc.worthit.tracking.lifecycle.domain.ItemDisposal;
import com.shaopc.worthit.tracking.lifecycle.domain.ItemDisposalRepository;
import com.shaopc.worthit.tracking.lifecycle.domain.ItemLifecycleStateMachine;
import com.shaopc.worthit.tracking.outbox.application.ReminderOutboxWriter;
import com.shaopc.worthit.tracking.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 编排物品处置状态机、幂等、本地事务与提醒 Outbox。
 */
@Service
@RequiredArgsConstructor
public class ItemLifecycleServiceImpl
        implements ItemLifecycleService {

    private static final int MAX_REMARK_LENGTH = 512;

    private final ItemRepository itemRepository;
    private final ItemDisposalRepository disposalRepository;
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
}
