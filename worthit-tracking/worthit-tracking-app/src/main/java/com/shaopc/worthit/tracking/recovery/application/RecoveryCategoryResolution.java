package com.shaopc.worthit.tracking.recovery.application;

import com.shaopc.worthit.tracking.category.domain.Category;

/**
 * 完整恢复的目标分类及是否发生回落。
 */
public record RecoveryCategoryResolution(
        Category category,
        boolean fallbackApplied) {
}
