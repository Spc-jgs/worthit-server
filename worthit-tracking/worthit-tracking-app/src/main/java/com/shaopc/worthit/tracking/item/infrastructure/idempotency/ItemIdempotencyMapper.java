package com.shaopc.worthit.tracking.item.infrastructure.idempotency;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * Item 创建幂等记录 Mapper。
 */
@Mapper
public interface ItemIdempotencyMapper {

    /**
     * 首次调用占位；唯一键冲突时不覆盖原记录。
     */
    @Insert("""
            INSERT IGNORE INTO trk_idempotency_record (
                id, user_id, operation_code, idempotency_key,
                request_hash, status, processing_expire_at,
                expires_at, create_time, update_time
            ) VALUES (
                #{id}, #{userId}, #{operationCode},
                #{idempotencyKey}, #{requestHash}, #{status},
                #{processingExpireAt}, #{expiresAt},
                #{createTime}, #{updateTime}
            )
            """)
    int insertClaim(ItemIdempotencyDO record);

    /**
     * 锁定指定幂等记录，串行重放与首次结果写入。
     */
    @Select("""
            SELECT id, user_id, operation_code,
                   idempotency_key, request_hash,
                   response_json, status,
                   processing_expire_at, expires_at,
                   create_time, update_time
            FROM trk_idempotency_record
            WHERE user_id = #{userId}
              AND operation_code = #{operationCode}
              AND idempotency_key = #{idempotencyKey}
            FOR UPDATE
            """)
    ItemIdempotencyDO selectForUpdate(
            @Param("userId") long userId,
            @Param("operationCode") String operationCode,
            @Param("idempotencyKey") String idempotencyKey);

    /**
     * 完成首次成功调用。
     */
    @Update("""
            UPDATE trk_idempotency_record
            SET response_json = #{responseJson},
                status = 'SUCCEEDED',
                processing_expire_at = NULL,
                update_time = #{updateTime}
            WHERE user_id = #{userId}
              AND operation_code = #{operationCode}
              AND idempotency_key = #{idempotencyKey}
              AND request_hash = #{requestHash}
              AND status = 'PROCESSING'
            """)
    int complete(
            @Param("userId") long userId,
            @Param("operationCode") String operationCode,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("requestHash") String requestHash,
            @Param("responseJson") String responseJson,
            @Param("updateTime") LocalDateTime updateTime);
}
