package com.shaopc.worthit.tracking.wish.application;

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
import com.shaopc.worthit.tracking.item.application.ItemDetail;
import com.shaopc.worthit.tracking.item.domain.Item;
import com.shaopc.worthit.tracking.item.domain.ItemCost;
import com.shaopc.worthit.tracking.item.domain.ItemCostCalculator;
import com.shaopc.worthit.tracking.item.domain.ItemLifecycleStatus;
import com.shaopc.worthit.tracking.item.domain.ItemRepository;
import com.shaopc.worthit.tracking.item.domain.ItemWithCategory;
import com.shaopc.worthit.tracking.outbox.application.ReminderOutboxWriter;
import com.shaopc.worthit.tracking.restore.application.RestoreClaimCoordinator;
import com.shaopc.worthit.tracking.restore.application.RestoreWindowPolicy;
import com.shaopc.worthit.tracking.security.CurrentUserProvider;
import com.shaopc.worthit.tracking.wish.domain.Wish;
import com.shaopc.worthit.tracking.wish.domain.WishCost;
import com.shaopc.worthit.tracking.wish.domain.WishCostCalculator;
import com.shaopc.worthit.tracking.wish.domain.WishDeletionState;
import com.shaopc.worthit.tracking.wish.domain.WishErrorCode;
import com.shaopc.worthit.tracking.wish.domain.WishRepository;
import com.shaopc.worthit.tracking.wish.domain.WishStatus;
import com.shaopc.worthit.tracking.wish.domain.WishWithCategory;
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
 * 编排 Wish CRUD、状态命令、购买转物品和短时恢复。
 */
@Service
@RequiredArgsConstructor
public class WishServiceImpl implements WishService {

    private final WishRepository wishRepository;
    private final ItemRepository itemRepository;
    private final CategoryReferenceResolver categoryReferenceResolver;
    private final IdempotencyStore idempotencyStore;
    private final RequestDigest requestDigest;
    private final RestoreTokenStore restoreTokenStore;
    private final ReminderOutboxWriter outboxWriter;
    private final CurrentUserProvider currentUserProvider;
    private final Clock trackingClock;
    private final RestoreWindowPolicy restoreWindowPolicy;
    private final RestoreClaimCoordinator restoreClaimCoordinator;

    /** 幂等新建想买。 */
    @Transactional
    @Override
    public WishDetail create(
            String idempotencyKey,
            CreateWishCommand command) {
        long userId = currentUserId();
        LocalDate today = today();
        CreateWishCommand normalized = normalize(command);
        validate(normalized, today);
        String hash = requestDigest.hash(normalized);
        IdempotencyClaim<WishDetail> claim = claim(
                userId, TrackingOperation.WISH_CREATE,
                idempotencyKey,
                hash, WishDetail.class);
        if (claim.status() == IdempotencyClaim.Status.REPLAY) {
            return claim.replay();
        }

        Category category = categoryReferenceResolver.resolve(
                normalized.categoryId(), userId);
        LocalDateTime now = now();
        boolean enabled = reminderEnabled(normalized);
        Wish saved = wishRepository.create(new Wish(
                0, userId, category.id(), normalized.name(),
                normalized.expectedPrice(),
                normalized.expectedYears(),
                normalized.residualValue(),
                normalized.reason(), normalized.remark(),
                normalized.watchDeadline(), enabled,
                WishStatus.CONSIDERING, null, null, null, null,
                1, now, now));
        if (enabled && !saved.watchDeadline().isBefore(today)) {
            writeExpectation(
                    saved, true,
                    ReminderOperationType.INITIAL_SYNC);
        }
        WishDetail result = toDetail(
                new WishWithCategory(saved, category.name()));
        complete(
                userId, TrackingOperation.WISH_CREATE,
                idempotencyKey,
                hash, result);
        return result;
    }

    /** 查询当前用户想买详情。 */
    @Transactional(readOnly = true)
    @Override
    public WishDetail detail(long wishId) {
        return toDetail(required(wishId, currentUserId()));
    }

    /** 分页查询当前用户想买。 */
    @Transactional(readOnly = true)
    @Override
    public PageResult<WishSummary> list(
            int page,
            int size,
            String keyword,
            Long categoryId) {
        PageQuery query = new PageQuery(page, size);
        PageResult<WishWithCategory> result =
                wishRepository.findPage(
                        currentUserId(), query,
                        normalizeNullableText(keyword),
                        categoryId);
        List<WishSummary> items = result.getItems()
                .stream().map(this::toSummary).toList();
        return PageResult.of(items, query, result.getTotal());
    }

    /** 按版本更新考虑中的想买。 */
    @Transactional
    @Override
    public WishDetail update(
            long wishId,
            String idempotencyKey,
            UpdateWishCommand command) {
        long userId = currentUserId();
        LocalDate today = today();
        UpdateWishCommand normalized = normalize(command);
        validate(normalized, today);
        String hash = requestDigest.hash(
                new UpdateDigest(wishId, normalized));
        IdempotencyClaim<WishDetail> claim = claim(
                userId, TrackingOperation.WISH_UPDATE,
                idempotencyKey,
                hash, WishDetail.class);
        if (claim.status() == IdempotencyClaim.Status.REPLAY) {
            return claim.replay();
        }

        WishWithCategory current = required(wishId, userId);
        Wish previous = current.wish();
        if (!WishStatus.CONSIDERING.equals(previous.status())
                || previous.version() != normalized.version()) {
            throw stateConflict();
        }
        Category category = categoryReferenceResolver.resolve(
                normalized.categoryId(), userId);
        LocalDateTime now = now();
        Wish changed = new Wish(
                previous.id(), previous.userId(), category.id(),
                normalized.name(), normalized.expectedPrice(),
                normalized.expectedYears(),
                normalized.residualValue(), normalized.reason(),
                normalized.remark(), normalized.watchDeadline(),
                normalized.watchReminderEnabled(),
                previous.status(), previous.lastAbandonReason(),
                previous.lastAbandonAt(),
                previous.convertedItemId(),
                previous.conversionKey(),
                previous.version() + 1,
                previous.createTime(), now);
        if (!wishRepository.update(
                changed, normalized.version())) {
            throw stateConflict();
        }
        writeUpdatedExpectation(previous, changed);
        WishDetail result = toDetail(
                new WishWithCategory(changed, category.name()));
        complete(
                userId, TrackingOperation.WISH_UPDATE,
                idempotencyKey,
                hash, result);
        return result;
    }

    /**
     * 将考虑中的想买幂等转换为唯一物品。
     *
     * <p>TECH-DB-001：先锁 Wish，再由
     * {@code uk_item_source_wish} 提供数据库最终防线。</p>
     */
    @Transactional
    @Override
    public WishPurchaseResult purchase(
            long wishId,
            long version,
            String idempotencyKey) {
        long userId = currentUserId();
        validateVersion(version);
        PurchaseDigest digest = new PurchaseDigest(wishId, version);
        String hash = requestDigest.hash(digest);
        IdempotencyClaim<WishPurchaseResult> claim = claim(
                userId, TrackingOperation.WISH_PURCHASE,
                idempotencyKey,
                hash, WishPurchaseResult.class);
        if (claim.status() == IdempotencyClaim.Status.REPLAY) {
            return claim.replay();
        }

        WishWithCategory locked = wishRepository.findForUpdate(
                        wishId, userId)
                .orElseThrow(() -> new BusinessException(
                        CommonWebErrorCode.RES_NOT_FOUND));
        Wish wish = locked.wish();
        if (WishStatus.PURCHASED.equals(wish.status())
                && wish.convertedItemId() != null) {
            WishPurchaseResult replay =
                    existingPurchase(locked, userId);
            complete(
                    userId, TrackingOperation.WISH_PURCHASE,
                    idempotencyKey,
                    hash, replay);
            return replay;
        }
        if (!WishStatus.CONSIDERING.equals(wish.status())
                || wish.version() != version) {
            throw stateConflict();
        }

        Category category = categoryReferenceResolver.resolve(
                wish.categoryId(), userId);
        LocalDateTime now = now();
        Item item = itemRepository.createFromWish(new Item(
                0, userId, wish.categoryId(), wish.name(),
                wish.expectedPrice(), wish.expectedYears(),
                wish.residualValue(), null, null, false,
                null, null, ItemLifecycleStatus.HOLDING, 1, now, now),
                wish.id());
        String conversionKey = "wish:" + wish.id();
        if (!wishRepository.purchase(
                wish.id(), userId, version, item.id(),
                conversionKey, now)) {
            throw stateConflict();
        }
        Wish purchased = copyStatus(
                wish, WishStatus.PURCHASED, version + 1,
                item.id(), conversionKey, now);
        writeExpectation(
                purchased, false,
                ReminderOperationType.PURCHASE_WISH);
        WishPurchaseResult result = new WishPurchaseResult(
                toDetail(new WishWithCategory(
                        purchased, category.name())),
                toItemDetail(
                        new ItemWithCategory(
                                item, category.name())));
        complete(
                userId, TrackingOperation.WISH_PURCHASE,
                idempotencyKey,
                hash, result);
        return result;
    }

    /** 放弃考虑中的想买，仅保留最近一次原因和时间。 */
    @Transactional
    @Override
    public WishDetail abandon(
            long wishId,
            long version,
            String reason,
            String idempotencyKey) {
        return changeStatus(
                wishId, version,
                normalizeNullableText(reason),
                idempotencyKey,
                TrackingOperation.WISH_ABANDON,
                WishStatus.CONSIDERING,
                WishStatus.ABANDONED,
                ReminderOperationType.ABANDON_WISH);
    }

    /** 将已放弃想买重新置为考虑中。 */
    @Transactional
    @Override
    public WishDetail reconsider(
            long wishId,
            long version,
            String idempotencyKey) {
        return changeStatus(
                wishId, version, null, idempotencyKey,
                TrackingOperation.WISH_RECONSIDER,
                WishStatus.ABANDONED,
                WishStatus.CONSIDERING,
                ReminderOperationType.CONTINUE_CONSIDERING);
    }

    /** 逻辑删除想买并签发短时恢复令牌。 */
    @Transactional
    @Override
    public DeleteWishResult delete(
            long wishId,
            long version,
            String idempotencyKey) {
        long userId = currentUserId();
        validateVersion(version);
        DeleteDigest digest = new DeleteDigest(wishId, version);
        String hash = requestDigest.hash(digest);
        IdempotencyClaim<DeleteWishResult> claim = claim(
                userId, TrackingOperation.WISH_DELETE,
                idempotencyKey,
                hash, DeleteWishResult.class);
        if (claim.status() == IdempotencyClaim.Status.REPLAY) {
            return claim.replay();
        }
        Wish wish = required(wishId, userId).wish();
        if (wish.version() != version) {
            throw stateConflict();
        }
        LocalDateTime now = now();
        if (!wishRepository.delete(
                wishId, userId, version, now)) {
            throw stateConflict();
        }
        Wish deleted = copyStatus(
                wish, wish.status(), version + 1,
                wish.convertedItemId(),
                wish.conversionKey(), now);
        writeExpectation(
                deleted, false,
                ReminderOperationType.DELETE_OBJECT);
        LocalDateTime deadline =
                restoreWindowPolicy.deadlineFrom(now);
        String token = restoreTokenStore.issue(
                userId, TrackingOperation.WISH_RESTORE, wishId,
                version + 1, deadline);
        DeleteWishResult result =
                new DeleteWishResult(wishId, deadline, token);
        complete(
                userId, TrackingOperation.WISH_DELETE,
                idempotencyKey,
                hash, result);
        return result;
    }

    /** 在短时窗口内幂等恢复想买，不自动恢复旧提醒。 */
    @Transactional
    @Override
    public WishDetail restore(
            long wishId,
            long deletedVersion,
            String restoreToken) {
        long userId = currentUserId();
        WishDeletionState deletion =
                wishRepository.findDeletionState(
                                wishId, userId)
                        .orElseThrow(() -> new BusinessException(
                                CommonWebErrorCode.RES_NOT_FOUND));
        RestoreTokenClaim<WishDetail> claim =
                restoreClaimCoordinator
                        .claimWithCategoryReservation(
                                userId,
                                deletion.wish().categoryId(),
                                TrackingOperation.WISH_RESTORE,
                                wishId,
                                deletedVersion,
                                restoreToken,
                                now(),
                                WishDetail.class);
        if (claim.status() == RestoreTokenClaim.Status.REPLAY) {
            return claim.replay();
        }
        if (claim.status() != RestoreTokenClaim.Status.AVAILABLE) {
            throw stateConflict();
        }
        if (!deletion.deleted()
                || deletion.wish().version()
                != deletedVersion) {
            throw stateConflict();
        }
        if (!wishRepository.restore(
                        wishId, userId,
                        deletedVersion, now())) {
            throw stateConflict();
        }
        WishDetail result = toDetail(required(wishId, userId));
        restoreTokenStore.complete(
                userId, TrackingOperation.WISH_RESTORE, wishId,
                deletedVersion, restoreToken, result);
        return result;
    }

    private WishDetail changeStatus(
            long wishId,
            long version,
            String abandonReason,
            String idempotencyKey,
            TrackingOperation operation,
            WishStatus expectedStatus,
            WishStatus targetStatus,
            ReminderOperationType reminderOperation) {
        long userId = currentUserId();
        validateVersion(version);
        StatusDigest digest = new StatusDigest(
                wishId, version, abandonReason);
        String hash = requestDigest.hash(digest);
        IdempotencyClaim<WishDetail> claim = claim(
                userId, operation, idempotencyKey,
                hash, WishDetail.class);
        if (claim.status() == IdempotencyClaim.Status.REPLAY) {
            return claim.replay();
        }
        WishWithCategory current = required(wishId, userId);
        Wish wish = current.wish();
        if (!expectedStatus.equals(wish.status())
                || wish.version() != version) {
            throw stateConflict();
        }
        LocalDateTime now = now();
        String savedReason = WishStatus.ABANDONED.equals(targetStatus)
                ? abandonReason : wish.lastAbandonReason();
        LocalDateTime savedAt = WishStatus.ABANDONED.equals(targetStatus)
                ? now : wish.lastAbandonAt();
        if (!wishRepository.changeStatus(
                wishId, userId, version,
                expectedStatus, targetStatus,
                savedReason, savedAt, now)) {
            throw stateConflict();
        }
        Wish changed = new Wish(
                wish.id(), wish.userId(), wish.categoryId(),
                wish.name(), wish.expectedPrice(),
                wish.expectedYears(), wish.residualValue(),
                wish.reason(), wish.remark(),
                wish.watchDeadline(),
                wish.watchReminderEnabled(),
                targetStatus, savedReason, savedAt,
                wish.convertedItemId(), wish.conversionKey(),
                version + 1, wish.createTime(), now);
        boolean enable = WishStatus.CONSIDERING.equals(targetStatus)
                && changed.watchReminderEnabled()
                && changed.watchDeadline() != null
                && !changed.watchDeadline().isBefore(today());
        writeExpectation(changed, enable, reminderOperation);
        WishDetail result = toDetail(
                new WishWithCategory(
                        changed, current.categoryName()));
        complete(
                userId, operation, idempotencyKey,
                hash, result);
        return result;
    }

    private void writeUpdatedExpectation(
            Wish previous, Wish updated) {
        ReminderOperationType operation;
        if (previous.watchReminderEnabled()
                && !updated.watchReminderEnabled()) {
            operation = ReminderOperationType.DISABLE_REMINDER;
        } else if (!previous.watchReminderEnabled()
                && updated.watchReminderEnabled()) {
            operation = ReminderOperationType.ENABLE_REMINDER;
        } else if (!Objects.equals(
                previous.watchDeadline(),
                updated.watchDeadline())) {
            operation =
                    ReminderOperationType.UPDATE_BUSINESS_DATE;
        } else {
            return;
        }
        writeExpectation(
                updated,
                updated.watchReminderEnabled(),
                operation);
    }

    private void writeExpectation(
            Wish wish,
            boolean enabled,
            ReminderOperationType operation) {
        LocalDate deadline = wish.watchDeadline();
        outboxWriter.write(new ReconcileReminderCommand(
                wish.userId(),
                ReminderBusinessType.WISH,
                wish.id(),
                ReminderType.WATCH,
                wish.version(),
                deadline,
                deadline == null
                        ? null : deadline.atStartOfDay(),
                enabled,
                wish.status().code(),
                operation,
                ReminderClientContract.SCHEMA_VERSION));
    }

    private WishPurchaseResult existingPurchase(
            WishWithCategory locked, long userId) {
        ItemWithCategory item =
                itemRepository.findBySourceWishId(
                                locked.wish().id(), userId)
                        .orElseThrow(() -> new IllegalStateException(
                                "已购买想买缺少转换物品"));
        return new WishPurchaseResult(
                toDetail(locked), toItemDetail(item));
    }

    private WishDetail toDetail(WishWithCategory view) {
        Wish wish = view.wish();
        WishCost cost = WishCostCalculator.calculate(
                wish.expectedPrice(),
                wish.expectedYears(),
                wish.residualValue());
        return new WishDetail(
                wish.id(), wish.name(), wish.categoryId(),
                view.categoryName(),
                plain(wish.expectedPrice()),
                plain(wish.expectedYears()),
                plain(wish.residualValue()),
                cost.residualUnset(), wish.reason(),
                wish.remark(), wish.watchDeadline(),
                wish.watchReminderEnabled(),
                wish.status().code(),
                wish.lastAbandonReason(),
                wish.lastAbandonAt(),
                wish.convertedItemId(),
                cost.expectedUseDays(),
                cost.planDailyCost().toPlainString(),
                cost.planDailyCostDisplay(),
                cost.planDailyCostTiny(),
                wish.version(), wish.createTime(),
                wish.updateTime());
    }

    private WishSummary toSummary(WishWithCategory view) {
        Wish wish = view.wish();
        WishCost cost = WishCostCalculator.calculate(
                wish.expectedPrice(),
                wish.expectedYears(),
                wish.residualValue());
        return new WishSummary(
                wish.id(), wish.name(), view.categoryName(),
                plain(wish.expectedPrice()),
                cost.planDailyCostDisplay(),
                cost.residualUnset(), wish.watchDeadline(),
                wish.status().code(), wish.version(),
                wish.createTime());
    }

    private ItemDetail toItemDetail(ItemWithCategory view) {
        Item item = view.item();
        ItemCost cost = ItemCostCalculator.calculate(
                item.purchasePrice(), item.expectedYears(),
                item.residualValue(), item.purchaseDate(), today());
        return new ItemDetail(
                item.id(), item.name(), item.categoryId(),
                view.categoryName(), plain(item.purchasePrice()),
                plain(item.expectedYears()),
                plain(item.residualValue()),
                cost.residualUnset(), item.purchaseDate(),
                item.warrantyExpireDate(),
                item.warrantyReminderEnabled(),
                item.brandModel(), item.remark(),
                item.lifecycleStatus().code(),
                cost.expectedUseDays(),
                cost.planDailyCost().toPlainString(),
                cost.planDailyCostDisplay(),
                cost.planDailyCostTiny(),
                cost.holdingDays(),
                cost.holdingDailyCost() == null
                        ? null
                        : cost.holdingDailyCost().toPlainString(),
                cost.holdingDailyCostDisplay(),
                null,
                item.version(), item.createTime(),
                item.updateTime());
    }

    private WishWithCategory required(
            long wishId, long userId) {
        return wishRepository.findByIdAndUserId(wishId, userId)
                .orElseThrow(() -> new BusinessException(
                        CommonWebErrorCode.RES_NOT_FOUND));
    }

    private <T> IdempotencyClaim<T> claim(
            long userId,
            TrackingOperation operation,
            String key,
            String hash,
            Class<T> type) {
        IdempotencyClaim<T> claim = idempotencyStore.claim(
                userId, operation, key, hash, type);
        if (claim.status() == IdempotencyClaim.Status.CONFLICT) {
            throw new BusinessException(
                    WishErrorCode.IDEM_CONFLICT);
        }
        return claim;
    }

    private <T> void complete(
            long userId,
            TrackingOperation operation,
            String key,
            String hash,
            T result) {
        idempotencyStore.complete(
                userId, operation, key, hash, result);
    }

    private static CreateWishCommand normalize(
            CreateWishCommand command) {
        return new CreateWishCommand(
                command.name().trim(), command.categoryId(),
                scale(command.expectedPrice()),
                scaleYears(command.expectedYears()),
                scaleNullable(command.residualValue()),
                normalizeNullableText(command.reason()),
                normalizeNullableText(command.remark()),
                command.watchDeadline(),
                command.watchReminderEnabled());
    }

    private static UpdateWishCommand normalize(
            UpdateWishCommand command) {
        return new UpdateWishCommand(
                command.version(), command.name().trim(),
                command.categoryId(),
                scale(command.expectedPrice()),
                scaleYears(command.expectedYears()),
                scaleNullable(command.residualValue()),
                normalizeNullableText(command.reason()),
                normalizeNullableText(command.remark()),
                command.watchDeadline(),
                command.watchReminderEnabled());
    }

    private static void validate(
            CreateWishCommand command, LocalDate today) {
        boolean enabled = reminderEnabled(command);
        validateValues(
                command.expectedPrice(),
                command.expectedYears(),
                command.residualValue(),
                command.watchDeadline(), enabled, today);
    }

    private static void validate(
            UpdateWishCommand command, LocalDate today) {
        validateVersion(command.version());
        validateValues(
                command.expectedPrice(),
                command.expectedYears(),
                command.residualValue(),
                command.watchDeadline(),
                command.watchReminderEnabled(), today);
    }

    private static void validateValues(
            BigDecimal price,
            BigDecimal years,
            BigDecimal residual,
            LocalDate deadline,
            boolean reminderEnabled,
            LocalDate today) {
        if (price.signum() < 0
                || years.signum() <= 0
                || residual != null && residual.signum() < 0
                || reminderEnabled && deadline == null
                || reminderEnabled && deadline.isBefore(today)) {
            throw new BusinessException(
                    CommonWebErrorCode.VAL_INVALID_ARGUMENT);
        }
    }

    private static boolean reminderEnabled(
            CreateWishCommand command) {
        return command.watchReminderEnabled() == null
                ? command.watchDeadline() != null
                : command.watchReminderEnabled();
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(6, RoundingMode.UNNECESSARY);
    }

    private static BigDecimal scaleNullable(BigDecimal value) {
        return value == null ? null : scale(value);
    }

    private static BigDecimal scaleYears(BigDecimal value) {
        return value.setScale(3, RoundingMode.UNNECESSARY);
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

    private static void validateVersion(long version) {
        if (version <= 0) {
            throw new BusinessException(
                    CommonWebErrorCode.VAL_INVALID_ARGUMENT);
        }
    }

    private static Wish copyStatus(
            Wish wish,
            WishStatus status,
            long version,
            Long itemId,
            String conversionKey,
            LocalDateTime now) {
        return new Wish(
                wish.id(), wish.userId(), wish.categoryId(),
                wish.name(), wish.expectedPrice(),
                wish.expectedYears(), wish.residualValue(),
                wish.reason(), wish.remark(),
                wish.watchDeadline(),
                wish.watchReminderEnabled(), status,
                wish.lastAbandonReason(),
                wish.lastAbandonAt(), itemId, conversionKey,
                version, wish.createTime(), now);
    }

    private static BusinessException stateConflict() {
        return new BusinessException(
                WishErrorCode.VAL_STATE_CONFLICT);
    }

    private LocalDate today() {
        return LocalDate.now(trackingClock);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(trackingClock);
    }

    private long currentUserId() {
        return currentUserProvider.currentUser().userId();
    }

    private record UpdateDigest(
            long wishId, UpdateWishCommand command) {
    }

    private record PurchaseDigest(long wishId, long version) {
    }

    private record DeleteDigest(long wishId, long version) {
    }

    private record StatusDigest(
            long wishId, long version, String reason) {
    }
}
