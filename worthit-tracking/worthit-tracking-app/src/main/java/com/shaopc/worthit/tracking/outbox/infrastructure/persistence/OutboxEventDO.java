package com.shaopc.worthit.tracking.outbox.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * {@code trk_outbox_event} 持久化对象。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("trk_outbox_event")
public class OutboxEventDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String eventId;
    private String aggregateType;
    private Long aggregateId;
    private Long userId;
    private Long sourceVersion;
    private String eventType;
    private String payloadJson;
    private Integer schemaVersion;
    private String status;
    private Integer retryCount;
    private LocalDateTime nextRetryAt;
    private String lockedBy;
    private LocalDateTime lockedAt;
    private String lastError;
    private LocalDateTime processedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
