package com.shaopc.worthit.tracking.subscription.domain;

import com.shaopc.worthit.common.core.pagination.PageQuery;
import com.shaopc.worthit.common.core.pagination.PageResult;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 订阅聚合持久化边界。
 */
public interface SubscriptionRepository {

    Subscription create(Subscription subscription);

    Optional<SubscriptionWithCategory> findByIdAndUserId(
            long subscriptionId,
            long userId);

    Optional<SubscriptionDeletionState> findDeletionState(
            long subscriptionId,
            long userId);

    PageResult<SubscriptionWithCategory> findPage(
            long userId,
            PageQuery pageQuery,
            String keyword,
            Long categoryId);

    boolean update(
            Subscription subscription,
            long expectedVersion);

    boolean changeStatus(
            long subscriptionId,
            long userId,
            long expectedVersion,
            String expectedStatus,
            String targetStatus,
            LocalDateTime now);

    boolean resume(
            Subscription subscription,
            long expectedVersion,
            String expectedStatus);

    boolean delete(
            long subscriptionId,
            long userId,
            long expectedVersion,
            LocalDateTime now);

    boolean restore(
            long subscriptionId,
            long userId,
            long deletedVersion,
            LocalDateTime now);
}
