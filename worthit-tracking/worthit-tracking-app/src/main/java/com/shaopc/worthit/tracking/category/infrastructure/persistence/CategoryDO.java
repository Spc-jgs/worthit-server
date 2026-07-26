package com.shaopc.worthit.tracking.category.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * {@code trk_category} 持久化对象。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("trk_category")
public class CategoryDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private String name;
    private String systemCode;
    private Long version;
    private Long createBy;
    private LocalDateTime createTime;
    private Long updateBy;
    private LocalDateTime updateTime;
    private Boolean delFlag;
    private LocalDateTime deleteTime;
}
