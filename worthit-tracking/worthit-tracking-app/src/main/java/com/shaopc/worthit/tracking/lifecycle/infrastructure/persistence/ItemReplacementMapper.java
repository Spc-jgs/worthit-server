package com.shaopc.worthit.tracking.lifecycle.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 替换关系 Mapper。
 */
@Mapper
public interface ItemReplacementMapper
        extends BaseMapper<ItemReplacementDO> {
}
