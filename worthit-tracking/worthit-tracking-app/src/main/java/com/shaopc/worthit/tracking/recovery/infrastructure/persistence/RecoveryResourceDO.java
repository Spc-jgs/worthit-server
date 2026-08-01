package com.shaopc.worthit.tracking.recovery.infrastructure.persistence;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 已删除 Tracking 资源联合查询投影。
 */
@Getter
@Setter
@NoArgsConstructor
public class RecoveryResourceDO {

    private Long id;
    private String resourceType;
    private String name;
    private Long categoryId;
    private String categoryName;
    private Boolean categoryAvailable;
    private String status;
    private Long version;
    private LocalDateTime deletedAt;
}
