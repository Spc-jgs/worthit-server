package com.shaopc.worthit.tracking.recovery.application;

import com.shaopc.worthit.tracking.recovery.domain.RecoveryResourceType;

/**
 * 完整恢复幂等摘要命令。
 */
public record FullRestoreCommand(
        RecoveryResourceType resourceType,
        long resourceId,
        long version) {
}
