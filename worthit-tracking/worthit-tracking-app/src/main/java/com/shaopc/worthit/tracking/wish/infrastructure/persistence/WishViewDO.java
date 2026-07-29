package com.shaopc.worthit.tracking.wish.infrastructure.persistence;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 想买与分类联合查询结果。
 */
@Getter
@Setter
@NoArgsConstructor
public class WishViewDO extends WishDO {

    private String categoryName;
}
