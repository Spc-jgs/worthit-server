package com.shaopc.worthit.tracking.recovery.application;

import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.core.pagination.PageQuery;
import com.shaopc.worthit.common.core.pagination.PageResult;
import com.shaopc.worthit.common.web.error.CommonWebErrorCode;
import com.shaopc.worthit.tracking.idempotency.application.IdempotencyClaim;
import com.shaopc.worthit.tracking.idempotency.application.IdempotencyErrorCode;
import com.shaopc.worthit.tracking.idempotency.application.IdempotencyStore;
import com.shaopc.worthit.tracking.idempotency.application.RequestDigest;
import com.shaopc.worthit.tracking.idempotency.application.TrackingOperation;
import com.shaopc.worthit.tracking.item.domain.ItemDeletionState;
import com.shaopc.worthit.tracking.item.domain.ItemRepository;
import com.shaopc.worthit.tracking.item.domain.ItemWithCategory;
import com.shaopc.worthit.tracking.recovery.domain.DeletedResource;
import com.shaopc.worthit.tracking.recovery.domain.RecoveryErrorCode;
import com.shaopc.worthit.tracking.recovery.domain.RecoveryRepository;
import com.shaopc.worthit.tracking.recovery.domain.RecoveryResourceType;
import com.shaopc.worthit.tracking.security.CurrentUserProvider;
import com.shaopc.worthit.tracking.subscription.domain.SubscriptionDeletionState;
import com.shaopc.worthit.tracking.subscription.domain.SubscriptionRepository;
import com.shaopc.worthit.tracking.subscription.domain.SubscriptionWithCategory;
import com.shaopc.worthit.tracking.wish.domain.WishDeletionState;
import com.shaopc.worthit.tracking.wish.domain.WishRepository;
import com.shaopc.worthit.tracking.wish.domain.WishWithCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 编排三类 Tracking 资源的已删除列表与长期恢复。
 */
@Service
@RequiredArgsConstructor
public class RecoveryServiceImpl implements RecoveryService {

    private final RecoveryRepository recoveryRepository;
    private final ItemRepository itemRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final WishRepository wishRepository;
    private final RecoveryCategoryResolver categoryResolver;
    private final IdempotencyStore idempotencyStore;
    private final RequestDigest requestDigest;
    private final CurrentUserProvider currentUserProvider;
    private final Clock trackingClock;

    @Transactional(readOnly = true)
    @Override
    public PageResult<RecoveryResourceSummary> list(
            RecoveryResourceType resourceType,
            int page,
            int size) {
        PageQuery pageQuery = new PageQuery(page, size);
        PageResult<DeletedResource> result =
                recoveryRepository.findDeletedPage(
                        currentUserId(),
                        resourceType,
                        pageQuery);
        List<RecoveryResourceSummary> items = result.getItems()
                .stream()
                .map(RecoveryServiceImpl::toSummary)
                .toList();
        return PageResult.of(items, pageQuery, result.getTotal());
    }

    @Transactional
    @Override
    public RecoveryResult restore(
            RecoveryResourceType resourceType,
            long resourceId,
            long version,
            String idempotencyKey) {
        if (resourceType == null
                || resourceId <= 0
                || version <= 0) {
            throw new BusinessException(
                    CommonWebErrorCode.VAL_INVALID_ARGUMENT);
        }
        long userId = currentUserId();
        TrackingOperation operation = operation(resourceType);
        FullRestoreCommand command = new FullRestoreCommand(
                resourceType, resourceId, version);
        String requestHash = requestDigest.hash(command);
        IdempotencyClaim<RecoveryResult> claim =
                idempotencyStore.claim(
                        userId,
                        operation,
                        idempotencyKey,
                        requestHash,
                        RecoveryResult.class);
        if (claim.status() == IdempotencyClaim.Status.CONFLICT) {
            throw new BusinessException(
                    IdempotencyErrorCode.IDEM_CONFLICT);
        }
        if (claim.status() == IdempotencyClaim.Status.REPLAY) {
            return claim.replay();
        }

        RecoveryResult result = switch (resourceType) {
            case ITEM -> restoreItem(
                    userId, resourceId, version);
            case SUBSCRIPTION -> restoreSubscription(
                    userId, resourceId, version);
            case WISH -> restoreWish(
                    userId, resourceId, version);
        };
        idempotencyStore.complete(
                userId,
                operation,
                idempotencyKey,
                requestHash,
                result);
        return result;
    }

    private RecoveryResult restoreItem(
            long userId,
            long resourceId,
            long version) {
        ItemDeletionState state = itemRepository
                .findDeletionState(resourceId, userId)
                .orElseThrow(RecoveryServiceImpl::notFound);
        requireDeletedVersion(
                state.deleted(), state.item().version(), version);
        RecoveryCategoryResolution category = categoryResolver
                .resolve(state.item().categoryId(), userId);
        if (!itemRepository.restoreToCategory(
                resourceId,
                userId,
                version,
                category.category().id(),
                now())) {
            throw stateConflict();
        }
        ItemWithCategory restored = itemRepository
                .findByIdAndUserId(resourceId, userId)
                .orElseThrow(() -> new IllegalStateException(
                        "Item完整恢复后无法读取"));
        return new RecoveryResult(
                restored.item().id(),
                RecoveryResourceType.ITEM,
                restored.item().name(),
                restored.item().categoryId(),
                restored.categoryName(),
                restored.item().lifecycleStatus().code(),
                restored.item().version(),
                category.fallbackApplied());
    }

    private RecoveryResult restoreSubscription(
            long userId,
            long resourceId,
            long version) {
        SubscriptionDeletionState state = subscriptionRepository
                .findDeletionState(resourceId, userId)
                .orElseThrow(RecoveryServiceImpl::notFound);
        requireDeletedVersion(
                state.deleted(),
                state.subscription().version(),
                version);
        RecoveryCategoryResolution category = categoryResolver
                .resolve(
                        state.subscription().categoryId(),
                        userId);
        if (!subscriptionRepository.restoreToCategory(
                resourceId,
                userId,
                version,
                category.category().id(),
                now())) {
            throw stateConflict();
        }
        SubscriptionWithCategory restored = subscriptionRepository
                .findByIdAndUserId(resourceId, userId)
                .orElseThrow(() -> new IllegalStateException(
                        "Subscription完整恢复后无法读取"));
        return new RecoveryResult(
                restored.subscription().id(),
                RecoveryResourceType.SUBSCRIPTION,
                restored.subscription().name(),
                restored.subscription().categoryId(),
                restored.categoryName(),
                restored.subscription().status().code(),
                restored.subscription().version(),
                category.fallbackApplied());
    }

    private RecoveryResult restoreWish(
            long userId,
            long resourceId,
            long version) {
        WishDeletionState state = wishRepository
                .findDeletionState(resourceId, userId)
                .orElseThrow(RecoveryServiceImpl::notFound);
        requireDeletedVersion(
                state.deleted(), state.wish().version(), version);
        RecoveryCategoryResolution category = categoryResolver
                .resolve(state.wish().categoryId(), userId);
        if (!wishRepository.restoreToCategory(
                resourceId,
                userId,
                version,
                category.category().id(),
                now())) {
            throw stateConflict();
        }
        WishWithCategory restored = wishRepository
                .findByIdAndUserId(resourceId, userId)
                .orElseThrow(() -> new IllegalStateException(
                        "Wish完整恢复后无法读取"));
        return new RecoveryResult(
                restored.wish().id(),
                RecoveryResourceType.WISH,
                restored.wish().name(),
                restored.wish().categoryId(),
                restored.categoryName(),
                restored.wish().status().code(),
                restored.wish().version(),
                category.fallbackApplied());
    }

    private static RecoveryResourceSummary toSummary(
            DeletedResource resource) {
        return new RecoveryResourceSummary(
                resource.id(),
                resource.resourceType(),
                resource.name(),
                resource.categoryId(),
                resource.categoryName(),
                resource.categoryAvailable(),
                resource.status(),
                resource.version(),
                resource.deletedAt());
    }

    private static TrackingOperation operation(
            RecoveryResourceType resourceType) {
        return switch (resourceType) {
            case ITEM -> TrackingOperation.ITEM_FULL_RESTORE;
            case SUBSCRIPTION ->
                    TrackingOperation.SUB_FULL_RESTORE;
            case WISH -> TrackingOperation.WISH_FULL_RESTORE;
        };
    }

    private static void requireDeletedVersion(
            boolean deleted,
            long actualVersion,
            long expectedVersion) {
        if (!deleted || actualVersion != expectedVersion) {
            throw stateConflict();
        }
    }

    private static BusinessException notFound() {
        return new BusinessException(
                CommonWebErrorCode.RES_NOT_FOUND);
    }

    private static BusinessException stateConflict() {
        return new BusinessException(
                RecoveryErrorCode.VAL_STATE_CONFLICT);
    }

    private long currentUserId() {
        return currentUserProvider.currentUser().userId();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(trackingClock);
    }
}
