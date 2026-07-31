package com.shaopc.worthit.tracking.lifecycle.infrastructure.persistence;

import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.tracking.item.domain.ItemErrorCode;
import com.shaopc.worthit.tracking.lifecycle.domain.ItemReplacement;
import com.shaopc.worthit.tracking.lifecycle.domain.ItemReplacementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/**
 * 使用 MyBatis-Plus 保存不可变替换关系。
 */
@Repository
@RequiredArgsConstructor
public class MybatisItemReplacementRepository
        implements ItemReplacementRepository {

    private final ItemReplacementMapper mapper;

    @Override
    public ItemReplacement save(ItemReplacement replacement) {
        ItemReplacementDO data = new ItemReplacementDO();
        data.setId(replacement.id());
        data.setUserId(replacement.userId());
        data.setOldItemId(replacement.oldItemId());
        data.setNewItemId(replacement.newItemId());
        data.setCreateTime(replacement.createTime());
        try {
            mapper.insert(data);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(
                    ItemErrorCode.VAL_STATE_CONFLICT);
        }
        return replacement;
    }
}
