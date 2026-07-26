package com.shaopc.worthit.tracking.item.infrastructure.outbox;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Tracking Outbox Mapper。
 */
@Mapper
public interface OutboxEventMapper
        extends BaseMapper<OutboxEventDO> {
}
