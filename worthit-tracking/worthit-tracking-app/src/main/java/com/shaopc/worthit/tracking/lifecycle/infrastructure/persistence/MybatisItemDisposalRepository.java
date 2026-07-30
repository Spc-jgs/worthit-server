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
    public Optional<ItemDisposal> findByItemIdAndUserId(
            long itemId, long userId) {
        return Optional.ofNullable(
                        mapper.selectByItemAndUser(
                                itemId, userId))
                .map(MybatisItemDisposalRepository::toDomain);
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
