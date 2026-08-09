package com.shaopc.worthit.tracking.idempotency.infrastructure;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * Tracking 幂等记录 Mapper。
 */
@Mapper
public interface IdempotencyMapper {

    /**
     * 首次调用占位；唯一键冲突时不覆盖原记录。
     */
    @Insert("""
            INSERT INTO trk_idempotency_record (
                id, user_id, operation_code, idempotency_key,
                request_hash, status, processing_expire_at,
                expires_at, create_time, update_time
            ) VALUES (
                #{id}, #{userId}, #{operationCode},
                #{idempotencyKey}, #{requestHash}, #{status},
                #{processingExpireAt}, #{expiresAt},
                #{createTime}, #{updateTime}
            )
            ON DUPLICATE KEY UPDATE
                id = trk_idempotency_record.id
            """)
    int insertClaim(IdempotencyDO record);

    /**
     * 无锁探测既有记录；既有重放无需占用用户写围栏。
     */
    @Select("""
            SELECT id
            FROM trk_idempotency_record
            WHERE user_id = #{userId}
              AND operation_code = #{operationCode}
              AND idempotency_key = #{idempotencyKey}
            LIMIT 1
            """)
    Long selectExistingId(
            @Param("userId") long userId,
            @Param("operationCode") String operationCode,
            @Param("idempotencyKey") String idempotencyKey);

    /**
     * 锁定指定幂等记录，串行重放与首次结果写入。
     */
    @Select("""
            SELECT id, user_id, operation_code,
                   idempotency_key, request_hash,
                   response_json, status,
                   error_code, error_message,
                   processing_expire_at, expires_at,
                   create_time, update_time
            FROM trk_idempotency_record
            WHERE user_id = #{userId}
              AND operation_code = #{operationCode}
              AND idempotency_key = #{idempotencyKey}
            FOR UPDATE
            """)
    IdempotencyDO selectForUpdate(
            @Param("userId") long userId,
            @Param("operationCode") String operationCode,
            @Param("idempotencyKey") String idempotencyKey);

    /**
     * 完成首次成功调用。
     */
    @Update("""
            UPDATE trk_idempotency_record
            SET response_json = #{responseJson},
                status = #{targetStatus},
                processing_expire_at = NULL,
                update_time = #{updateTime}
            WHERE user_id = #{userId}
              AND operation_code = #{operationCode}
              AND idempotency_key = #{idempotencyKey}
              AND request_hash = #{requestHash}
              AND status = #{expectedStatus}
            """)
    int complete(
            @Param("userId") long userId,
            @Param("operationCode") String operationCode,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("requestHash") String requestHash,
            @Param("responseJson") String responseJson,
            @Param("targetStatus") String targetStatus,
            @Param("expectedStatus") String expectedStatus,
            @Param("updateTime") LocalDateTime updateTime);

    /**
     * 以旧租约时间作为 fencing token 接管过期执行。
     */
    @Update("""
            UPDATE trk_idempotency_record
            SET processing_expire_at = #{newLeaseExpiresAt},
                expires_at = #{expiresAt},
                response_json = NULL,
                error_code = NULL,
                error_message = NULL,
                update_time = #{updateTime}
            WHERE user_id = #{userId}
              AND operation_code = #{operationCode}
              AND idempotency_key = #{idempotencyKey}
              AND request_hash = #{requestHash}
              AND status = #{processingStatus}
              AND processing_expire_at = #{oldLeaseExpiresAt}
            """)
    int reclaimExecution(
            @Param("userId") long userId,
            @Param("operationCode") String operationCode,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("requestHash") String requestHash,
            @Param("oldLeaseExpiresAt")
            LocalDateTime oldLeaseExpiresAt,
            @Param("newLeaseExpiresAt")
            LocalDateTime newLeaseExpiresAt,
            @Param("expiresAt") LocalDateTime expiresAt,
            @Param("processingStatus") String processingStatus,
            @Param("updateTime") LocalDateTime updateTime);

    /**
     * 只有当前租约持有者可以在业务事务内提交成功结果。
     */
    @Update("""
            UPDATE trk_idempotency_record
            SET response_json = #{responseJson},
                status = #{targetStatus},
                error_code = NULL,
                error_message = NULL,
                processing_expire_at = NULL,
                update_time = #{updateTime}
            WHERE user_id = #{userId}
              AND operation_code = #{operationCode}
              AND idempotency_key = #{idempotencyKey}
              AND request_hash = #{requestHash}
              AND status = #{expectedStatus}
              AND processing_expire_at = #{leaseExpiresAt}
            """)
    int completeExecutionSuccess(
            @Param("userId") long userId,
            @Param("operationCode") String operationCode,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("requestHash") String requestHash,
            @Param("leaseExpiresAt")
            LocalDateTime leaseExpiresAt,
            @Param("responseJson") String responseJson,
            @Param("targetStatus") String targetStatus,
            @Param("expectedStatus") String expectedStatus,
            @Param("updateTime") LocalDateTime updateTime);

    /**
     * 只有当前租约持有者可以固化终结性业务失败。
     */
    @Update("""
            UPDATE trk_idempotency_record
            SET response_json = NULL,
                status = #{targetStatus},
                error_code = #{errorCode},
                error_message = #{errorMessage},
                processing_expire_at = NULL,
                update_time = #{updateTime}
            WHERE user_id = #{userId}
              AND operation_code = #{operationCode}
              AND idempotency_key = #{idempotencyKey}
              AND request_hash = #{requestHash}
              AND status = #{expectedStatus}
              AND processing_expire_at = #{leaseExpiresAt}
            """)
    int completeExecutionFailure(
            @Param("userId") long userId,
            @Param("operationCode") String operationCode,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("requestHash") String requestHash,
            @Param("leaseExpiresAt")
            LocalDateTime leaseExpiresAt,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage,
            @Param("targetStatus") String targetStatus,
            @Param("expectedStatus") String expectedStatus,
            @Param("updateTime") LocalDateTime updateTime);
}
