package com.shaopc.worthit.tracking.lifecycle.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 处置事实 Mapper。
 */
@Mapper
public interface ItemDisposalMapper
        extends BaseMapper<ItemDisposalDO> {

    /**
     * 按物品与用户查询唯一处置事实。
     */
    @Select("""
            SELECT id, user_id, item_id, disposal_type,
                   disposal_date, purchase_price_snapshot,
                   sale_amount, remark, create_time, update_time
            FROM trk_item_disposal
            WHERE item_id = #{itemId}
              AND user_id = #{userId}
            """)
    ItemDisposalDO selectByItemAndUser(
            @Param("itemId") long itemId,
            @Param("userId") long userId);
}
