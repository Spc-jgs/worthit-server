package com.shaopc.worthit.tracking.subscription.application;

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
import com.shaopc.worthit.tracking.idempotency.application.IdempotencyClaim;
import com.shaopc.worthit.tracking.idempotency.application.IdempotencyStore;
import com.shaopc.worthit.tracking.idempotency.application.RequestDigest;
import com.shaopc.worthit.tracking.idempotency.application.RestoreTokenClaim;
import com.shaopc.worthit.tracking.idempotency.application.RestoreTokenStore;
import com.shaopc.worthit.tracking.outbox.application.ReminderOutboxWriter;
import com.shaopc.worthit.tracking.security.CurrentUserProvider;
import com.shaopc.worthit.tracking.subscription.domain.AutoRenew;
import com.shaopc.worthit.tracking.subscription.domain.BillingCycleType;
import com.shaopc.worthit.tracking.subscription.domain.Subscription;
import com.shaopc.worthit.tracking.subscription.domain.SubscriptionCostCalculator;
import com.shaopc.worthit.tracking.subscription.domain.SubscriptionDeletionState;
import com.shaopc.worthit.tracking.subscription.domain.SubscriptionErrorCode;
import com.shaopc.worthit.tracking.subscription.domain.SubscriptionMonthlyCost;
import com.shaopc.worthit.tracking.subscription.domain.SubscriptionRepository;
import com.shaopc.worthit.tracking.subscription.domain.SubscriptionWithCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 编排订阅 CRUD、状态命令、提醒联动和短时恢复。
 */
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private static final long RESTORE_WINDOW_SECONDS = 60;
    private static final String SUB_CREATE = "SUB_CREATE";
    private static final String SUB_UPDATE = "SUB_UPDATE";
    private static final String SUB_PAUSE = "SUB_PAUSE";
    private static final String SUB_END = "SUB_END";
    private static final String SUB_RESUME = "SUB_RESUME";
    private static final String SUB_DELETE = "SUB_DELETE";
    private static final String SUB_RESTORE = "SUB_RESTORE";

    private final SubscriptionRepository repository;
    private final CategoryRepository categoryRepository;
    private final IdempotencyStore idempotencyStore;
    private final RequestDigest requestDigest;
    private final ReminderOutboxWriter outboxWriter;
    private final RestoreTokenStore restoreTokenStore;
    private final CurrentUserProvider currentUserProvider;
    private final Clock trackingClock;

    /**
     * 幂等创建订阅。
     */
    @Transactional
    public SubscriptionDetail create(
            String idempotencyKey,
            CreateSubscriptionCommand command) {
        long userId = currentUserId();
        CreateSubscriptionCommand normalized =
                normalize(command);
        validate(normalized);
        String requestHash = requestDigest.hash(normalized);
        IdempotencyClaim<SubscriptionDetail> claim =
                claim(
                        userId,
                        SUB_CREATE,
                        idempotencyKey,
                        requestHash,
                        SubscriptionDetail.class);
        if (claim.status() == IdempotencyClaim.Status.REPLAY) {
            return claim.replay();
        }

        Category category = resolveCategory(
                normalized.categoryId(), userId);
        LocalDateTime now = now();
        boolean reminderEnabled =
                reminderEnabled(normalized);
        Subscription saved = repository.create(
                new Subscription(
                        0,
                        userId,
                        category.id(),
                        normalized.name(),
                        normalized.amount(),
                        normalized.currency(),
                        normalized.billingCycleType(),
                        normalized.billingCycleValue(),
                        normalized.cnyReferenceAmount(),
                        normalized.nextRenewalDate(),
                        normalized.autoRenew(),
                        reminderEnabled,
                        Subscription.ACTIVE,
                        normalized.remark(),
                        1,
                        now,
                        now));
        if (reminderEnabled) {
            writeExpectation(
                    saved,
                    ReminderOperationType.INITIAL_SYNC);
        }
        SubscriptionDetail detail = toDetail(
                new SubscriptionWithCategory(
                        saved, category.name()));
        complete(
                userId,
                SUB_CREATE,
                idempotencyKey,
                requestHash,
                detail);
        return detail;
    }

    /**
     * 查询当前用户订阅详情。
     */
    @Transactional(readOnly = true)
    public SubscriptionDetail detail(long subscriptionId) {
        return toDetail(required(
                subscriptionId, currentUserId()));
    }

    /**
     * 分页查询当前用户订阅。
     */
    @Transactional(readOnly = true)
    public PageResult<SubscriptionSummary> list(
            int page,
            int size,
            String keyword,
            Long categoryId) {
        PageQuery pageQuery = new PageQuery(page, size);
        PageResult<SubscriptionWithCategory> result =
                repository.findPage(
                        currentUserId(),
                        pageQuery,
                        normalizeNullableText(keyword),
                        categoryId);
        List<SubscriptionSummary> items = result
                .getItems()
                .stream()
                .map(this::toSummary)
                .toList();
        return PageResult.of(
                items, pageQuery, result.getTotal());
    }

    /**
     * 按乐观锁版本更新订阅。
     */
    @Transactional
    public SubscriptionDetail update(
            long subscriptionId,
            String idempotencyKey,
            UpdateSubscriptionCommand command) {
        long userId = currentUserId();
        UpdateSubscriptionCommand normalized =
                normalize(command);
        validate(normalized);
        String requestHash = requestDigest.hash(
                new UpdateDigest(
                        subscriptionId, normalized));
        IdempotencyClaim<SubscriptionDetail> claim =
                claim(
                        userId,
                        SUB_UPDATE,
                        idempotencyKey,
                        requestHash,
                        SubscriptionDetail.class);
        if (claim.status() == IdempotencyClaim.Status.REPLAY) {
            return claim.replay();
        }

        Subscription existing = required(
                subscriptionId, userId).subscription();
        requireVersion(existing, normalized.version());
        Category category = resolveCategory(
                normalized.categoryId(), userId);
        boolean reminderEnabled =
                reminderEnabled(normalized);
        Subscription updated = new Subscription(
                existing.id(),
                existing.userId(),
                category.id(),
                normalized.name(),
                normalized.amount(),
                normalized.currency(),
                normalized.billingCycleType(),
                normalized.billingCycleValue(),
                normalized.cnyReferenceAmount(),
                normalized.nextRenewalDate(),
                normalized.autoRenew(),
                reminderEnabled,
                existing.status(),
                normalized.remark(),
                existing.version() + 1,
                existing.createTime(),
                now());
        if (!repository.update(
                updated, normalized.version())) {
            throw stateConflict();
        }
        writeUpdatedExpectation(existing, updated);
        SubscriptionDetail detail = toDetail(
                new SubscriptionWithCategory(
                        updated, category.name()));
        complete(
                userId,
                SUB_UPDATE,
                idempotencyKey,
                requestHash,
                detail);
        return detail;
    }

    /**
     * 暂停有效订阅。
     */
    @Transactional
    public SubscriptionDetail pause(
            long subscriptionId,
            long version,
            String idempotencyKey) {
        return changeStatus(
                subscriptionId,
                version,
                idempotencyKey,
                SUB_PAUSE,
                Subscription.PAUSED,
                ReminderOperationType.PAUSE_SUBSCRIPTION);
    }

    /**
     * 结束有效或已暂停订阅。
     */
    @Transactional
    public SubscriptionDetail end(
            long subscriptionId,
            long version,
            String idempotencyKey) {
        return changeStatus(
                subscriptionId,
                version,
                idempotencyKey,
                SUB_END,
                Subscription.ENDED,
                ReminderOperationType.END_SUBSCRIPTION);
    }

    /**
     * 恢复暂停或结束的订阅。
     */
    @Transactional
    public SubscriptionDetail resume(
            long subscriptionId,
            String idempotencyKey,
            ResumeSubscriptionCommand command) {
        long userId = currentUserId();
        validateVersion(command.version());
        String requestHash = requestDigest.hash(
                new ResumeDigest(
                        subscriptionId, command));
        IdempotencyClaim<SubscriptionDetail> claim =
                claim(
                        userId,
                        SUB_RESUME,
                        idempotencyKey,
                        requestHash,
                        SubscriptionDetail.class);
        if (claim.status() == IdempotencyClaim.Status.REPLAY) {
            return claim.replay();
        }

        SubscriptionWithCategory view = required(
                subscriptionId, userId);
        Subscription existing = view.subscription();
        requireVersion(existing, command.version());
        if (!Subscription.PAUSED.equals(existing.status())
                && !Subscription.ENDED.equals(
                        existing.status())) {
            throw stateConflict();
        }

        LocalDate renewalDate =
                command.nextRenewalDate() == null
                        ? existing.nextRenewalDate()
                        : command.nextRenewalDate();
        boolean reminderEnabled =
                command.renewalReminderEnabled() == null
                        ? existing.renewalReminderEnabled()
                        : command.renewalReminderEnabled();
        if (reminderEnabled
                && (renewalDate == null
                || !renewalDate.isAfter(
                        LocalDate.now(trackingClock)))) {
            throw stateConflict();
        }

        Subscription resumed = copyAtVersion(
                existing,
                Subscription.ACTIVE,
                renewalDate,
                reminderEnabled,
                existing.version() + 1,
                now());
        if (!repository.resume(
                resumed,
                command.version(),
                existing.status())) {
            throw stateConflict();
        }
        writeExpectation(
                resumed,
                ReminderOperationType.RESUME_SUBSCRIPTION);
        SubscriptionDetail detail = toDetail(
                new SubscriptionWithCategory(
                        resumed, view.categoryName()));
        complete(
                userId,
                SUB_RESUME,
                idempotencyKey,
                requestHash,
                detail);
        return detail;
    }

    /**
     * 逻辑删除订阅并签发短时恢复凭据。
     */
    @Transactional
    public DeleteSubscriptionResult delete(
            long subscriptionId,
            long version,
            String idempotencyKey) {
        long userId = currentUserId();
        validateVersion(version);
        SubscriptionVersionCommand command =
                new SubscriptionVersionCommand(
                        subscriptionId, version);
        String requestHash = requestDigest.hash(command);
        IdempotencyClaim<DeleteSubscriptionResult> claim =
                claim(
                        userId,
                        SUB_DELETE,
                        idempotencyKey,
                        requestHash,
                        DeleteSubscriptionResult.class);
        if (claim.status() == IdempotencyClaim.Status.REPLAY) {
            return claim.replay();
        }

        Subscription existing = required(
                subscriptionId, userId).subscription();
        requireVersion(existing, version);
        LocalDateTime now = now();
        if (!repository.delete(
                subscriptionId, userId, version, now)) {
            throw stateConflict();
        }
        long deletedVersion = version + 1;
        Subscription deleted = copyAtVersion(
                existing,
                existing.status(),
                existing.nextRenewalDate(),
                existing.renewalReminderEnabled(),
                deletedVersion,
                now);
        writeExpectation(
                deleted,
                false,
                ReminderOperationType.DELETE_OBJECT);

        LocalDateTime deadline =
                now.plusSeconds(RESTORE_WINDOW_SECONDS);
        String restoreToken = restoreTokenStore.issue(
                userId,
                SUB_RESTORE,
                subscriptionId,
                deletedVersion,
                deadline);
        DeleteSubscriptionResult result =
                new DeleteSubscriptionResult(
                        subscriptionId,
                        deadline,
                        restoreToken);
        complete(
                userId,
                SUB_DELETE,
                idempotencyKey,
                requestHash,
                result);
        return result;
    }

    /**
     * 在服务端窗口内恢复订阅，不自动恢复旧提醒。
     */
    @Transactional
    public SubscriptionDetail restore(
            long subscriptionId,
            long deletedVersion,
            String restoreToken) {
        long userId = currentUserId();
        SubscriptionDeletionState state =
                repository.findDeletionState(
                                subscriptionId, userId)
                        .orElseThrow(() ->
                                new BusinessException(
                                        CommonWebErrorCode
                                                .RES_NOT_FOUND));
        LocalDateTime now = now();
        RestoreTokenClaim<SubscriptionDetail> claim =
                restoreTokenStore.claim(
                        userId,
                        SUB_RESTORE,
                        subscriptionId,
                        deletedVersion,
                        restoreToken,
                        now,
                        SubscriptionDetail.class);
        if (claim.status()
                == RestoreTokenClaim.Status.REPLAY) {
            return claim.replay();
        }
        if (claim.status()
                != RestoreTokenClaim.Status.AVAILABLE
                || !state.deleted()
                || state.subscription().version()
                != deletedVersion) {
            throw stateConflict();
        }
        if (!repository.restore(
                subscriptionId,
                userId,
                deletedVersion,
                now)) {
            throw stateConflict();
        }
        SubscriptionDetail restored = detailForUser(
                subscriptionId, userId);
        restoreTokenStore.complete(
                userId,
                SUB_RESTORE,
                subscriptionId,
                deletedVersion,
                restoreToken,
                restored);
        return restored;
    }

    private SubscriptionDetail changeStatus(
            long subscriptionId,
            long version,
            String idempotencyKey,
            String operationCode,
            String targetStatus,
            ReminderOperationType reminderOperation) {
        long userId = currentUserId();
        validateVersion(version);
        SubscriptionVersionCommand command =
                new SubscriptionVersionCommand(
                        subscriptionId, version);
        String requestHash = requestDigest.hash(command);
        IdempotencyClaim<SubscriptionDetail> claim =
                claim(
                        userId,
                        operationCode,
                        idempotencyKey,
                        requestHash,
                        SubscriptionDetail.class);
        if (claim.status() == IdempotencyClaim.Status.REPLAY) {
            return claim.replay();
        }

        SubscriptionWithCategory view = required(
                subscriptionId, userId);
        Subscription existing = view.subscription();
        requireVersion(existing, version);
        boolean allowed = Subscription.PAUSED.equals(
                targetStatus)
                ? Subscription.ACTIVE.equals(existing.status())
                : Subscription.ACTIVE.equals(existing.status())
                || Subscription.PAUSED.equals(existing.status());
        if (!allowed) {
            throw stateConflict();
        }
        LocalDateTime now = now();
        if (!repository.changeStatus(
                subscriptionId,
                userId,
                version,
                existing.status(),
                targetStatus,
                now)) {
            throw stateConflict();
        }
        Subscription changed = copyAtVersion(
                existing,
                targetStatus,
                existing.nextRenewalDate(),
                existing.renewalReminderEnabled(),
                version + 1,
                now);
        writeExpectation(
                changed, false, reminderOperation);
        SubscriptionDetail detail = toDetail(
                new SubscriptionWithCategory(
                        changed, view.categoryName()));
        complete(
                userId,
                operationCode,
                idempotencyKey,
                requestHash,
                detail);
        return detail;
    }

    private void writeUpdatedExpectation(
            Subscription previous,
            Subscription updated) {
        if (!Subscription.ACTIVE.equals(updated.status())) {
            return;
        }
        ReminderOperationType operationType;
        if (previous.renewalReminderEnabled()
                && !updated.renewalReminderEnabled()) {
            operationType =
                    ReminderOperationType.DISABLE_REMINDER;
        } else if (!previous.renewalReminderEnabled()
                && updated.renewalReminderEnabled()) {
            operationType =
                    ReminderOperationType.ENABLE_REMINDER;
        } else if (!Objects.equals(
                previous.nextRenewalDate(),
                updated.nextRenewalDate())) {
            operationType =
                    ReminderOperationType.UPDATE_BUSINESS_DATE;
        } else {
            return;
        }
        writeExpectation(updated, operationType);
    }

    private void writeExpectation(
            Subscription subscription,
            ReminderOperationType operationType) {
        writeExpectation(
                subscription,
                Subscription.ACTIVE.equals(
                        subscription.status())
                        && subscription
                        .renewalReminderEnabled()
                        && subscription.nextRenewalDate()
                        != null,
                operationType);
    }

    private void writeExpectation(
            Subscription subscription,
            boolean reminderEnabled,
            ReminderOperationType operationType) {
        LocalDate businessDate =
                subscription.nextRenewalDate();
        outboxWriter.write(new ReconcileReminderCommand(
                subscription.userId(),
                ReminderBusinessType.SUBSCRIPTION,
                subscription.id(),
                ReminderType.RENEWAL,
                subscription.version(),
                businessDate,
                businessDate == null
                        ? null
                        : businessDate.minusDays(1)
                        .atStartOfDay(),
                reminderEnabled,
                subscription.status(),
                operationType,
                ReminderClientContract.SCHEMA_VERSION));
    }

    private SubscriptionWithCategory required(
            long subscriptionId,
            long userId) {
        return repository.findByIdAndUserId(
                        subscriptionId, userId)
                .orElseThrow(() -> new BusinessException(
                        CommonWebErrorCode.RES_NOT_FOUND));
    }

    private SubscriptionDetail detailForUser(
            long subscriptionId,
            long userId) {
        return toDetail(required(subscriptionId, userId));
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

    private SubscriptionDetail toDetail(
            SubscriptionWithCategory view) {
        Subscription subscription = view.subscription();
        SubscriptionMonthlyCost cost =
                SubscriptionCostCalculator.calculate(
                        subscription.amount(),
                        subscription.currency(),
                        subscription.billingCycleType(),
                        subscription.billingCycleValue(),
                        subscription.cnyReferenceAmount());
        return new SubscriptionDetail(
                subscription.id(),
                subscription.name(),
                subscription.categoryId(),
                view.categoryName(),
                subscription.amount().toPlainString(),
                subscription.currency(),
                subscription.billingCycleType().name(),
                subscription.billingCycleValue(),
                plain(subscription.cnyReferenceAmount()),
                subscription.nextRenewalDate(),
                subscription.autoRenew().name(),
                subscription.renewalReminderEnabled(),
                subscription.status(),
                subscription.remark(),
                cost.originalMonthlyCost().toPlainString(),
                cost.originalMonthlyCostDisplay(),
                plain(cost.cnyMonthlyCost()),
                cost.cnyMonthlyCostDisplay(),
                cost.cnyApproximate(),
                cost.includeInCnyTotal(),
                subscription.version(),
                subscription.createTime(),
                subscription.updateTime());
    }

    private SubscriptionSummary toSummary(
            SubscriptionWithCategory view) {
        Subscription subscription = view.subscription();
        SubscriptionMonthlyCost cost =
                SubscriptionCostCalculator.calculate(
                        subscription.amount(),
                        subscription.currency(),
                        subscription.billingCycleType(),
                        subscription.billingCycleValue(),
                        subscription.cnyReferenceAmount());
        return new SubscriptionSummary(
                subscription.id(),
                subscription.name(),
                view.categoryName(),
                subscription.amount().toPlainString(),
                subscription.currency(),
                cost.originalMonthlyCostDisplay(),
                cost.cnyMonthlyCostDisplay(),
                subscription.status(),
                subscription.nextRenewalDate(),
                subscription.version(),
                subscription.createTime());
    }

    private <T> IdempotencyClaim<T> claim(
            long userId,
            String operationCode,
            String idempotencyKey,
            String requestHash,
            Class<T> responseType) {
        IdempotencyClaim<T> claim =
                idempotencyStore.claim(
                        userId,
                        operationCode,
                        idempotencyKey,
                        requestHash,
                        responseType);
        if (claim.status()
                == IdempotencyClaim.Status.CONFLICT) {
            throw new BusinessException(
                    SubscriptionErrorCode.IDEM_CONFLICT);
        }
        return claim;
    }

    private <T> void complete(
            long userId,
            String operationCode,
            String idempotencyKey,
            String requestHash,
            T response) {
        idempotencyStore.complete(
                userId,
                operationCode,
                idempotencyKey,
                requestHash,
                response);
    }

    private static CreateSubscriptionCommand normalize(
            CreateSubscriptionCommand command) {
        return new CreateSubscriptionCommand(
                command.name().trim(),
                command.categoryId(),
                scale(command.amount()),
                command.currency()
                        .trim()
                        .toUpperCase(Locale.ROOT),
                command.billingCycleType(),
                command.billingCycleValue(),
                scaleNullable(command.cnyReferenceAmount()),
                command.nextRenewalDate(),
                command.autoRenew() == null
                        ? AutoRenew.UNKNOWN
                        : command.autoRenew(),
                command.renewalReminderEnabled(),
                normalizeNullableText(command.remark()));
    }

    private static UpdateSubscriptionCommand normalize(
            UpdateSubscriptionCommand command) {
        return new UpdateSubscriptionCommand(
                command.version(),
                command.name().trim(),
                command.categoryId(),
                scale(command.amount()),
                command.currency()
                        .trim()
                        .toUpperCase(Locale.ROOT),
                command.billingCycleType(),
                command.billingCycleValue(),
                scaleNullable(command.cnyReferenceAmount()),
                command.nextRenewalDate(),
                command.autoRenew() == null
                        ? AutoRenew.UNKNOWN
                        : command.autoRenew(),
                command.renewalReminderEnabled(),
                normalizeNullableText(command.remark()));
    }

    private static void validate(
            CreateSubscriptionCommand command) {
        validateValues(
                command.amount(),
                command.currency(),
                command.billingCycleType(),
                command.billingCycleValue(),
                command.cnyReferenceAmount(),
                reminderEnabled(command),
                command.nextRenewalDate());
    }

    private static void validate(
            UpdateSubscriptionCommand command) {
        validateVersion(command.version());
        validateValues(
                command.amount(),
                command.currency(),
                command.billingCycleType(),
                command.billingCycleValue(),
                command.cnyReferenceAmount(),
                reminderEnabled(command),
                command.nextRenewalDate());
    }

    private static void validateValues(
            BigDecimal amount,
            String currency,
            BillingCycleType cycleType,
            Integer cycleValue,
            BigDecimal cnyReferenceAmount,
            boolean reminderEnabled,
            LocalDate renewalDate) {
        boolean parameterized =
                cycleType == BillingCycleType.MULTI_MONTH
                        || cycleType
                        == BillingCycleType.FIXED_DAYS;
        if (amount.signum() < 0
                || !currency.matches("[A-Z]{3}")
                || parameterized
                != (cycleValue != null)
                || cycleValue != null && cycleValue <= 0
                || cnyReferenceAmount != null
                && cnyReferenceAmount.signum() < 0
                || "CNY".equals(currency)
                && cnyReferenceAmount != null
                || reminderEnabled && renewalDate == null) {
            throw new BusinessException(
                    CommonWebErrorCode.VAL_INVALID_ARGUMENT);
        }
    }

    private static boolean reminderEnabled(
            CreateSubscriptionCommand command) {
        return command.renewalReminderEnabled() == null
                ? command.nextRenewalDate() != null
                : command.renewalReminderEnabled();
    }

    private static boolean reminderEnabled(
            UpdateSubscriptionCommand command) {
        return command.renewalReminderEnabled() == null
                ? command.nextRenewalDate() != null
                : command.renewalReminderEnabled();
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(6, RoundingMode.UNNECESSARY);
    }

    private static BigDecimal scaleNullable(
            BigDecimal value) {
        return value == null ? null : scale(value);
    }

    private static String normalizeNullableText(
            String value) {
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

    private static void requireVersion(
            Subscription subscription,
            long expectedVersion) {
        if (subscription.version() != expectedVersion) {
            throw stateConflict();
        }
    }

    private static Subscription copyAtVersion(
            Subscription subscription,
            String status,
            LocalDate nextRenewalDate,
            boolean reminderEnabled,
            long version,
            LocalDateTime updateTime) {
        return new Subscription(
                subscription.id(),
                subscription.userId(),
                subscription.categoryId(),
                subscription.name(),
                subscription.amount(),
                subscription.currency(),
                subscription.billingCycleType(),
                subscription.billingCycleValue(),
                subscription.cnyReferenceAmount(),
                nextRenewalDate,
                subscription.autoRenew(),
                reminderEnabled,
                status,
                subscription.remark(),
                version,
                subscription.createTime(),
                updateTime);
    }

    private static BusinessException stateConflict() {
        return new BusinessException(
                SubscriptionErrorCode.VAL_STATE_CONFLICT);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(trackingClock);
    }

    private long currentUserId() {
        return currentUserProvider.currentUser().userId();
    }

    private record UpdateDigest(
            long subscriptionId,
            UpdateSubscriptionCommand command) {
    }

    private record ResumeDigest(
            long subscriptionId,
            ResumeSubscriptionCommand command) {
    }
}
