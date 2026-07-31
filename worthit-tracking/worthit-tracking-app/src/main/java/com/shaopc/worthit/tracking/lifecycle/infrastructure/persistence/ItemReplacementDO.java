package com.shaopc.worthit.tracking.lifecycle.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * {@code trk_item_replacement} 持久化对象。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("trk_item_replacement")
public class ItemReplacementDO {

    @TableId(type = IdType.INPUT)
    private Long id;
    private Long userId;
    private Long oldItemId;
    private Long newItemId;
    private LocalDateTime createTime;
}
