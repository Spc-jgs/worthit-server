package com.shaopc.worthit.tracking.outbox.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/**
 * Tracking Outbox Mapper。
 */
@Mapper
public interface OutboxEventMapper
        extends BaseMapper<OutboxEventDO> {

    /**
     * 锁定本批可投递或租约过期的事件。
     *
     * @param now 当前业务时间
     * @param leaseExpiredAt 租约过期边界
     * @param limit 批大小
     * @return 已锁定候选事件
     */
    @Select("""
            SELECT id, event_id, aggregate_type, aggregate_id,
                   user_id, source_version, event_type,
                   payload_json, schema_version, status,
                   retry_count, next_retry_at, locked_by,
                   locked_at, last_error, processed_at,
                   create_time, update_time
            FROM trk_outbox_event
            WHERE status = 'NEW'
               OR (
                    status = 'RETRY_WAIT'
                    AND (
                        next_retry_at IS NULL
                        OR next_retry_at <= #{now}
                    )
               )
               OR (
                    status = 'PROCESSING'
                    AND locked_at < #{leaseExpiredAt}
               )
            ORDER BY id
            LIMIT #{limit}
            FOR UPDATE SKIP LOCKED
            """)
    List<OutboxEventDO> selectClaimableForUpdate(
            @Param("now") LocalDateTime now,
            @Param("leaseExpiredAt") LocalDateTime leaseExpiredAt,
            @Param("limit") int limit);

    /**
     * 抢占单条事件并写入当前租约。
     */
    @Update("""
            UPDATE trk_outbox_event
            SET status = 'PROCESSING',
                locked_by = #{ownerId},
                locked_at = #{now},
                update_time = #{now}
            WHERE id = #{id}
              AND (
                    status = 'NEW'
                    OR (
                        status = 'RETRY_WAIT'
                        AND (
                            next_retry_at IS NULL
                            OR next_retry_at <= #{now}
                        )
                    )
                    OR (
                        status = 'PROCESSING'
                        AND locked_at < #{leaseExpiredAt}
                    )
              )
            """)
    int claim(
            @Param("id") long id,
            @Param("ownerId") String ownerId,
            @Param("now") LocalDateTime now,
            @Param("leaseExpiredAt") LocalDateTime leaseExpiredAt);

    /**
     * 仅允许当前租约持有者写入成功终态。
     */
    @Update("""
            UPDATE trk_outbox_event
            SET status = 'SUCCEEDED',
                next_retry_at = NULL,
                locked_by = NULL,
                locked_at = NULL,
                last_error = NULL,
                processed_at = #{now},
                update_time = #{now}
            WHERE id = #{id}
              AND status = 'PROCESSING'
              AND locked_by = #{ownerId}
            """)
    int markSucceeded(
            @Param("id") long id,
            @Param("ownerId") String ownerId,
            @Param("now") LocalDateTime now);

    /**
     * 仅允许当前租约持有者写入重试或死信状态。
     */
    @Update("""
            UPDATE trk_outbox_event
            SET status = #{status},
                retry_count = #{retryCount},
                next_retry_at = #{nextRetryAt},
                locked_by = NULL,
                locked_at = NULL,
                last_error = #{lastError},
                processed_at = #{processedAt},
                update_time = #{now}
            WHERE id = #{id}
              AND status = 'PROCESSING'
              AND locked_by = #{ownerId}
            """)
    int markFailed(
            @Param("id") long id,
            @Param("ownerId") String ownerId,
            @Param("status") String status,
            @Param("retryCount") int retryCount,
            @Param("nextRetryAt") LocalDateTime nextRetryAt,
            @Param("lastError") String lastError,
            @Param("processedAt") LocalDateTime processedAt,
            @Param("now") LocalDateTime now);
}
