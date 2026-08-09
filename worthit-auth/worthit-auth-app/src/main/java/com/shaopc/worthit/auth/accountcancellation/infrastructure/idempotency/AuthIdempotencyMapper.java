package com.shaopc.worthit.auth.accountcancellation.infrastructure.idempotency;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/** Auth 持久幂等 Mapper。 */
@Mapper
public interface AuthIdempotencyMapper {

    @Insert("""
            INSERT INTO auth_idempotency_record (
                id, user_id, operation_code, idempotency_key, request_hash,
                response_json, status, error_code, error_message,
                processing_expire_at, expires_at, create_time, update_time
            ) VALUES (
                #{id}, #{userId}, #{operationCode}, #{idempotencyKey}, #{requestHash},
                NULL, #{status}, NULL, NULL,
                #{processingExpireAt}, #{expiresAt}, #{createTime}, #{updateTime}
            )
            ON DUPLICATE KEY UPDATE
                id = auth_idempotency_record.id
            """)
    int insertClaim(AuthIdempotencyDO value);

    @Select("""
            SELECT id, user_id, operation_code, idempotency_key, request_hash,
                   response_json, status, error_code, error_message,
                   processing_expire_at, expires_at, create_time, update_time
              FROM auth_idempotency_record
             WHERE user_id = #{userId}
               AND operation_code = #{operationCode}
               AND idempotency_key = #{idempotencyKey}
               FOR UPDATE
            """)
    AuthIdempotencyDO selectForUpdate(
            @Param("userId") long userId,
            @Param("operationCode") String operationCode,
            @Param("idempotencyKey") String idempotencyKey);

    @Update("""
            UPDATE auth_idempotency_record
               SET processing_expire_at = #{newLease},
                   expires_at = #{expiresAt},
                   update_time = #{now}
             WHERE user_id = #{userId}
               AND operation_code = #{operationCode}
               AND idempotency_key = #{idempotencyKey}
               AND request_hash = #{requestHash}
               AND status = 'PROCESSING'
               AND processing_expire_at = #{oldLease}
            """)
    int reclaim(
            @Param("userId") long userId,
            @Param("operationCode") String operationCode,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("requestHash") String requestHash,
            @Param("oldLease") LocalDateTime oldLease,
            @Param("newLease") LocalDateTime newLease,
            @Param("expiresAt") LocalDateTime expiresAt,
            @Param("now") LocalDateTime now);

    @Update("""
            UPDATE auth_idempotency_record
               SET response_json = #{responseJson},
                   status = 'SUCCEEDED',
                   processing_expire_at = NULL,
                   update_time = #{now}
             WHERE user_id = #{userId}
               AND operation_code = #{operationCode}
               AND idempotency_key = #{idempotencyKey}
               AND request_hash = #{requestHash}
               AND status = 'PROCESSING'
               AND processing_expire_at = #{leaseExpiresAt}
            """)
    int completeSuccess(
            @Param("userId") long userId,
            @Param("operationCode") String operationCode,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("requestHash") String requestHash,
            @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt,
            @Param("responseJson") String responseJson,
            @Param("now") LocalDateTime now);

    @Update("""
            UPDATE auth_idempotency_record
               SET response_json = NULL,
                   status = 'FAILED',
                   error_code = #{errorCode},
                   error_message = #{errorMessage},
                   processing_expire_at = NULL,
                   update_time = #{now}
             WHERE user_id = #{userId}
               AND operation_code = #{operationCode}
               AND idempotency_key = #{idempotencyKey}
               AND request_hash = #{requestHash}
               AND status = 'PROCESSING'
               AND processing_expire_at = #{leaseExpiresAt}
            """)
    int completeFailure(
            @Param("userId") long userId,
            @Param("operationCode") String operationCode,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("requestHash") String requestHash,
            @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage,
            @Param("now") LocalDateTime now);
}
