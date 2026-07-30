package com.shaopc.worthit.tracking.lifecycle.infrastructure.persistence;

import com.shaopc.worthit.tracking.lifecycle.domain.DisposalType;
import com.shaopc.worthit.tracking.lifecycle.domain.ItemDisposal;
import com.shaopc.worthit.tracking.lifecycle.domain.ItemDisposalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 使用 MyBatis-Plus 持久化不可变处置事实。
 */
@Repository
@RequiredArgsConstructor
public class MybatisItemDisposalRepository
        implements ItemDisposalRepository {

    private final ItemDisposalMapper mapper;

    @Override
    public ItemDisposal save(ItemDisposal disposal) {
        mapper.insert(toData(disposal));
        return disposal;
    }

    @Override
    public Optional<ItemDisposal> findByItemIdAndUserId(
            long itemId, long userId) {
        return Optional.ofNullable(
                        mapper.selectByItemAndUser(
                                itemId, userId))
                .map(MybatisItemDisposalRepository::toDomain);
    }

    private static ItemDisposalDO toData(
            ItemDisposal disposal) {
        ItemDisposalDO data = new ItemDisposalDO();
        data.setId(disposal.id());
        data.setUserId(disposal.userId());
        data.setItemId(disposal.itemId());
        data.setDisposalType(disposal.type().code());
        data.setDisposalDate(disposal.disposalDate());
        data.setPurchasePriceSnapshot(
                disposal.purchasePriceSnapshot());
        data.setSaleAmount(disposal.saleAmount());
        data.setRemark(disposal.remark());
        data.setCreateTime(disposal.createTime());
        data.setUpdateTime(disposal.updateTime());
        return data;
    }

    private static ItemDisposal toDomain(
            ItemDisposalDO data) {
        return new ItemDisposal(
                data.getId(),
                data.getUserId(),
                data.getItemId(),
                DisposalType.fromCode(data.getDisposalType()),
                data.getDisposalDate(),
                data.getPurchasePriceSnapshot(),
                data.getSaleAmount(),
                data.getRemark(),
                data.getCreateTime(),
                data.getUpdateTime());
    }
}
