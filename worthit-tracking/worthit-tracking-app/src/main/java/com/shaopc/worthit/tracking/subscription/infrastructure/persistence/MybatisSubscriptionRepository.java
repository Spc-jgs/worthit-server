package com.shaopc.worthit.tracking.subscription.infrastructure.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shaopc.worthit.common.core.pagination.PageQuery;
import com.shaopc.worthit.common.core.pagination.PageResult;
import com.shaopc.worthit.tracking.subscription.domain.AutoRenew;
import com.shaopc.worthit.tracking.subscription.domain.BillingCycleType;
import com.shaopc.worthit.tracking.subscription.domain.Subscription;
import com.shaopc.worthit.tracking.subscription.domain.SubscriptionDeletionState;
import com.shaopc.worthit.tracking.subscription.domain.SubscriptionRepository;
import com.shaopc.worthit.tracking.subscription.domain.SubscriptionWithCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 使用 MyBatis-Plus 持久化 Subscription。
 */
@Repository
@RequiredArgsConstructor
public class MybatisSubscriptionRepository
        implements SubscriptionRepository {

    private final SubscriptionMapper mapper;

    @Override
    public Subscription create(Subscription subscription) {
        SubscriptionDO data = toData(subscription);
        mapper.insert(data);
        return toDomain(data);
    }

    @Override
    public Optional<SubscriptionWithCategory>
            findByIdAndUserId(
                    long subscriptionId,
                    long userId) {
        return Optional.ofNullable(
                        mapper.selectDetail(
                                subscriptionId, userId))
                .map(this::toView);
    }

    @Override
    public Optional<SubscriptionDeletionState>
            findDeletionState(
                    long subscriptionId,
                    long userId) {
        SubscriptionDO data = mapper.selectOne(
                Wrappers.<SubscriptionDO>lambdaQuery()
                        .eq(SubscriptionDO::getId, subscriptionId)
                        .eq(SubscriptionDO::getUserId, userId));
        return Optional.ofNullable(data)
                .map(row -> new SubscriptionDeletionState(
                        toDomain(row),
                        Boolean.TRUE.equals(row.getDelFlag())));
    }

    @Override
    public PageResult<SubscriptionWithCategory> findPage(
            long userId,
            PageQuery pageQuery,
            String keyword,
            Long categoryId) {
        long offset = (long) (pageQuery.page() - 1)
                * pageQuery.size();
        List<SubscriptionWithCategory> items =
                mapper.selectPage(
                                userId,
                                keyword,
                                categoryId,
                                offset,
                                pageQuery.size())
                        .stream()
                        .map(this::toView)
                        .toList();
        return PageResult.of(
                items,
                pageQuery,
                mapper.countPage(
                        userId, keyword, categoryId));
    }

    @Override
    public boolean update(
            Subscription subscription,
            long expectedVersion) {
        return mapper.updateByVersion(
                subscription, expectedVersion) == 1;
    }

    @Override
    public boolean changeStatus(
            long subscriptionId,
            long userId,
            long expectedVersion,
            String expectedStatus,
            String targetStatus,
            LocalDateTime now) {
        return mapper.changeStatus(
                subscriptionId,
                userId,
                expectedVersion,
                expectedStatus,
                targetStatus,
                now) == 1;
    }

    @Override
    public boolean resume(
            Subscription subscription,
            long expectedVersion,
            String expectedStatus) {
        return mapper.resume(
                subscription,
                expectedVersion,
                expectedStatus) == 1;
    }

    @Override
    public boolean delete(
            long subscriptionId,
            long userId,
            long expectedVersion,
            LocalDateTime now) {
        return mapper.deleteByVersion(
                subscriptionId,
                userId,
                expectedVersion,
                now) == 1;
    }

    @Override
    public boolean restore(
            long subscriptionId,
            long userId,
            long deletedVersion,
            LocalDateTime now) {
        return mapper.restoreByVersion(
                subscriptionId,
                userId,
                deletedVersion,
                now) == 1;
    }

    private SubscriptionDO toData(
            Subscription subscription) {
        SubscriptionDO data = new SubscriptionDO();
        data.setUserId(subscription.userId());
        data.setCategoryId(subscription.categoryId());
        data.setName(subscription.name());
        data.setAmount(subscription.amount());
        data.setCurrency(subscription.currency());
        data.setBillingCycleType(
                subscription.billingCycleType().name());
        data.setBillingCycleValue(
                subscription.billingCycleValue());
        data.setCnyReferenceAmount(
                subscription.cnyReferenceAmount());
        data.setNextRenewalDate(
                subscription.nextRenewalDate());
        data.setAutoRenew(subscription.autoRenew().name());
        data.setRenewalReminderEnabled(
                subscription.renewalReminderEnabled());
        data.setStatus(subscription.status());
        data.setRemark(subscription.remark());
        data.setVersion(subscription.version());
        data.setCreateBy(subscription.userId());
        data.setCreateTime(subscription.createTime());
        data.setUpdateBy(subscription.userId());
        data.setUpdateTime(subscription.updateTime());
        data.setDelFlag(false);
        return data;
    }

    private SubscriptionWithCategory toView(
            SubscriptionViewDO view) {
        return new SubscriptionWithCategory(
                new Subscription(
                        view.getId(),
                        view.getUserId(),
                        view.getCategoryId(),
                        view.getName(),
                        view.getAmount(),
                        view.getCurrency(),
                        BillingCycleType.valueOf(
                                view.getBillingCycleType()),
                        view.getBillingCycleValue(),
                        view.getCnyReferenceAmount(),
                        view.getNextRenewalDate(),
                        AutoRenew.valueOf(
                                view.getAutoRenew()),
                        view.getRenewalReminderEnabled(),
                        view.getStatus(),
                        view.getRemark(),
                        view.getVersion(),
                        view.getCreateTime(),
                        view.getUpdateTime()),
                view.getCategoryName());
    }

    private Subscription toDomain(SubscriptionDO data) {
        return new Subscription(
                data.getId(),
                data.getUserId(),
                data.getCategoryId(),
                data.getName(),
                data.getAmount(),
                data.getCurrency(),
                BillingCycleType.valueOf(
                        data.getBillingCycleType()),
                data.getBillingCycleValue(),
                data.getCnyReferenceAmount(),
                data.getNextRenewalDate(),
                AutoRenew.valueOf(data.getAutoRenew()),
                data.getRenewalReminderEnabled(),
                data.getStatus(),
                data.getRemark(),
                data.getVersion(),
                data.getCreateTime(),
                data.getUpdateTime());
    }
}
