package com.shaopc.worthit.tracking.wish.infrastructure.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shaopc.worthit.common.core.pagination.PageQuery;
import com.shaopc.worthit.common.core.pagination.PageResult;
import com.shaopc.worthit.tracking.wish.domain.Wish;
import com.shaopc.worthit.tracking.wish.domain.WishDeletionState;
import com.shaopc.worthit.tracking.wish.domain.WishRepository;
import com.shaopc.worthit.tracking.wish.domain.WishStatus;
import com.shaopc.worthit.tracking.wish.domain.WishWithCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 使用 MyBatis-Plus 持久化 Wish。
 */
@Repository
@RequiredArgsConstructor
public class MybatisWishRepository implements WishRepository {

    private final WishMapper mapper;

    @Override
    public Wish create(Wish wish) {
        WishDO data = toData(wish);
        mapper.insert(data);
        return toDomain(data);
    }

    @Override
    public Optional<WishWithCategory> findByIdAndUserId(
            long wishId, long userId) {
        return Optional.ofNullable(
                        mapper.selectDetail(wishId, userId))
                .map(this::toView);
    }

    @Override
    public Optional<WishWithCategory> findForUpdate(
            long wishId, long userId) {
        return Optional.ofNullable(
                        mapper.selectForUpdate(wishId, userId))
                .map(this::toView);
    }

    @Override
    public Optional<WishDeletionState> findDeletionState(
            long wishId, long userId) {
        WishDO data = mapper.selectOne(
                Wrappers.<WishDO>lambdaQuery()
                        .eq(WishDO::getId, wishId)
                        .eq(WishDO::getUserId, userId));
        return Optional.ofNullable(data)
                .map(row -> new WishDeletionState(
                        toDomain(row),
                        Boolean.TRUE.equals(row.getDelFlag()),
                        row.getDeleteTime()));
    }

    @Override
    public PageResult<WishWithCategory> findPage(
            long userId,
            PageQuery pageQuery,
            String keyword,
            Long categoryId) {
        long offset = (long) (pageQuery.page() - 1)
                * pageQuery.size();
        List<WishWithCategory> items = mapper.selectPage(
                        userId, keyword, categoryId,
                        offset, pageQuery.size())
                .stream().map(this::toView).toList();
        return PageResult.of(
                items,
                pageQuery,
                mapper.countPage(userId, keyword, categoryId));
    }

    @Override
    public boolean update(Wish wish, long expectedVersion) {
        return mapper.updateByVersion(
                wish,
                WishStatus.CONSIDERING.code(),
                expectedVersion) == 1;
    }

    @Override
    public boolean purchase(
            long wishId,
            long userId,
            long expectedVersion,
            long itemId,
            String conversionKey,
            LocalDateTime now) {
        return mapper.purchase(
                wishId, userId, expectedVersion,
                itemId, conversionKey,
                WishStatus.CONSIDERING.code(),
                WishStatus.PURCHASED.code(),
                now) == 1;
    }

    @Override
    public boolean changeStatus(
            long wishId,
            long userId,
            long expectedVersion,
            WishStatus expectedStatus,
            WishStatus targetStatus,
            String abandonReason,
            LocalDateTime abandonAt,
            LocalDateTime now) {
        return mapper.changeStatus(
                wishId, userId, expectedVersion,
                expectedStatus.code(), targetStatus.code(),
                abandonReason, abandonAt, now) == 1;
    }

    @Override
    public boolean delete(
            long wishId,
            long userId,
            long expectedVersion,
            LocalDateTime now) {
        return mapper.deleteByVersion(
                wishId, userId, expectedVersion, now) == 1;
    }

    @Override
    public boolean restore(
            long wishId,
            long userId,
            long deletedVersion,
            LocalDateTime now) {
        return mapper.restoreByVersion(
                wishId, userId, deletedVersion, now) == 1;
    }

    @Override
    public boolean restoreToCategory(
            long wishId,
            long userId,
            long deletedVersion,
            long categoryId,
            LocalDateTime now) {
        return mapper.restoreByVersionToCategory(
                wishId,
                userId,
                deletedVersion,
                categoryId,
                now) == 1;
    }

    private WishDO toData(Wish wish) {
        WishDO data = new WishDO();
        data.setUserId(wish.userId());
        data.setCategoryId(wish.categoryId());
        data.setName(wish.name());
        data.setExpectedPrice(wish.expectedPrice());
        data.setExpectedYears(wish.expectedYears());
        data.setResidualValue(wish.residualValue());
        data.setReason(wish.reason());
        data.setRemark(wish.remark());
        data.setWatchDeadline(wish.watchDeadline());
        data.setWatchReminderEnabled(
                wish.watchReminderEnabled());
        data.setStatus(wish.status().code());
        data.setLastAbandonReason(wish.lastAbandonReason());
        data.setLastAbandonAt(wish.lastAbandonAt());
        data.setConvertedItemId(wish.convertedItemId());
        data.setConversionKey(wish.conversionKey());
        data.setVersion(wish.version());
        data.setCreateBy(wish.userId());
        data.setCreateTime(wish.createTime());
        data.setUpdateBy(wish.userId());
        data.setUpdateTime(wish.updateTime());
        data.setDelFlag(false);
        return data;
    }

    private WishWithCategory toView(WishViewDO view) {
        return new WishWithCategory(
                toDomain(view), view.getCategoryName());
    }

    private Wish toDomain(WishDO data) {
        return new Wish(
                data.getId(), data.getUserId(),
                data.getCategoryId(), data.getName(),
                data.getExpectedPrice(), data.getExpectedYears(),
                data.getResidualValue(), data.getReason(),
                data.getRemark(), data.getWatchDeadline(),
                Boolean.TRUE.equals(
                        data.getWatchReminderEnabled()),
                WishStatus.fromCode(data.getStatus()),
                data.getLastAbandonReason(),
                data.getLastAbandonAt(),
                data.getConvertedItemId(),
                data.getConversionKey(), data.getVersion(),
                data.getCreateTime(), data.getUpdateTime());
    }
}
