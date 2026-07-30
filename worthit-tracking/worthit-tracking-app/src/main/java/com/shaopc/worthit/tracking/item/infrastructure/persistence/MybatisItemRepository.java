package com.shaopc.worthit.tracking.item.infrastructure.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shaopc.worthit.common.core.pagination.PageQuery;
import com.shaopc.worthit.common.core.pagination.PageResult;
import com.shaopc.worthit.tracking.item.domain.Item;
import com.shaopc.worthit.tracking.item.domain.ItemDeletionState;
import com.shaopc.worthit.tracking.item.domain.ItemLifecycleStatus;
import com.shaopc.worthit.tracking.item.domain.ItemRepository;
import com.shaopc.worthit.tracking.item.domain.ItemWithCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 使用 MyBatis-Plus 持久化 Item。
 */
@Repository
@RequiredArgsConstructor
public class MybatisItemRepository implements ItemRepository {

    private final ItemMapper itemMapper;

    @Override
    public Item create(Item item) {
        return create(item, null);
    }

    @Override
    public Item createFromWish(
            Item item, long sourceWishId) {
        return create(item, sourceWishId);
    }

    private Item create(Item item, Long sourceWishId) {
        ItemDO data = toData(item);
        data.setSourceWishId(sourceWishId);
        itemMapper.insert(data);
        return toDomain(data);
    }

    @Override
    public Optional<ItemWithCategory> findByIdAndUserId(
            long itemId, long userId) {
        return Optional.ofNullable(
                        itemMapper.selectDetail(itemId, userId))
                .map(this::toView);
    }

    @Override
    public Optional<ItemWithCategory> findBySourceWishId(
            long sourceWishId, long userId) {
        return Optional.ofNullable(
                        itemMapper.selectBySourceWishId(
                                sourceWishId, userId))
                .map(this::toView);
    }

    @Override
    public Optional<ItemDeletionState> findDeletionState(
            long itemId, long userId) {
        ItemDO data = itemMapper.selectOne(
                Wrappers.<ItemDO>lambdaQuery()
                        .eq(ItemDO::getId, itemId)
                        .eq(ItemDO::getUserId, userId));
        return Optional.ofNullable(data)
                .map(item -> new ItemDeletionState(
                        toDomain(item),
                        Boolean.TRUE.equals(item.getDelFlag()),
                        item.getDeleteTime()));
    }

    @Override
    public boolean update(Item item, long expectedVersion) {
        return itemMapper.updateByVersion(
                item, expectedVersion) == 1;
    }

    @Override
    public boolean dispose(
            long itemId,
            long userId,
            long expectedVersion,
            ItemLifecycleStatus targetStatus,
            LocalDateTime now) {
        return itemMapper.disposeByVersion(
                itemId,
                userId,
                expectedVersion,
                targetStatus.code(),
                now) == 1;
    }

    @Override
    public boolean delete(
            long itemId,
            long userId,
            long expectedVersion,
            LocalDateTime now) {
        return itemMapper.deleteByVersion(
                itemId, userId, expectedVersion, now) == 1;
    }

    @Override
    public boolean restore(
            long itemId,
            long userId,
            long deletedVersion,
            LocalDateTime now) {
        return itemMapper.restoreByVersion(
                itemId, userId, deletedVersion, now) == 1;
    }

    @Override
    public PageResult<ItemWithCategory> findPage(
            long userId,
            PageQuery pageQuery,
            String keyword,
            Long categoryId) {
        long offset = (long) (pageQuery.page() - 1)
                * pageQuery.size();
        List<ItemWithCategory> items = itemMapper.selectPage(
                        userId,
                        keyword,
                        categoryId,
                        offset,
                        pageQuery.size())
                .stream()
                .map(this::toView)
                .toList();
        long total = itemMapper.countPage(
                userId, keyword, categoryId);
        return PageResult.of(items, pageQuery, total);
    }

    private ItemDO toData(Item item) {
        ItemDO data = new ItemDO();
        data.setUserId(item.userId());
        data.setCategoryId(item.categoryId());
        data.setName(item.name());
        data.setPurchasePrice(item.purchasePrice());
        data.setExpectedYears(item.expectedYears());
        data.setResidualValue(item.residualValue());
        data.setPurchaseDate(item.purchaseDate());
        data.setWarrantyExpireDate(item.warrantyExpireDate());
        data.setWarrantyReminderEnabled(
                item.warrantyReminderEnabled());
        data.setBrandModel(item.brandModel());
        data.setRemark(item.remark());
        data.setLifecycleStatus(item.lifecycleStatus().code());
        data.setVersion(item.version());
        data.setCreateBy(item.userId());
        data.setCreateTime(item.createTime());
        data.setUpdateBy(item.userId());
        data.setUpdateTime(item.updateTime());
        data.setDelFlag(false);
        return data;
    }

    private ItemWithCategory toView(ItemViewDO view) {
        return new ItemWithCategory(
                new Item(
                        view.getId(),
                        view.getUserId(),
                        view.getCategoryId(),
                        view.getName(),
                        view.getPurchasePrice(),
                        view.getExpectedYears(),
                        view.getResidualValue(),
                        view.getPurchaseDate(),
                        view.getWarrantyExpireDate(),
                        view.getWarrantyReminderEnabled(),
                        view.getBrandModel(),
                        view.getRemark(),
                        ItemLifecycleStatus.fromCode(
                                view.getLifecycleStatus()),
                        view.getVersion(),
                        view.getCreateTime(),
                        view.getUpdateTime()),
                view.getCategoryName());
    }

    private Item toDomain(ItemDO data) {
        return new Item(
                data.getId(),
                data.getUserId(),
                data.getCategoryId(),
                data.getName(),
                data.getPurchasePrice(),
                data.getExpectedYears(),
                data.getResidualValue(),
                data.getPurchaseDate(),
                data.getWarrantyExpireDate(),
                data.getWarrantyReminderEnabled(),
                data.getBrandModel(),
                data.getRemark(),
                ItemLifecycleStatus.fromCode(
                        data.getLifecycleStatus()),
                data.getVersion(),
                data.getCreateTime(),
                data.getUpdateTime());
    }
}
