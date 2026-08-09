package com.shaopc.worthit.auth.accountcancellation.infrastructure.persistence;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/** Auth 账号注销状态与最终清理 Mapper。 */
@Mapper
public interface AccountCancellationMapper {

    @Select("""
            SELECT id, user_id, apply_at, effective_at, completed_at,
                   status, revoked_at, version, create_time, update_time
              FROM auth_account_cancellation
             WHERE user_id = #{userId}
               AND status IN ('PENDING', 'EXECUTING')
               FOR UPDATE
            """)
    AccountCancellationDO selectOpenForUpdate(@Param("userId") long userId);

    @Select("""
            SELECT id, user_id, apply_at, effective_at, completed_at,
                   status, revoked_at, version, create_time, update_time
              FROM auth_account_cancellation
             WHERE user_id = #{userId}
             ORDER BY create_time DESC, id DESC
             LIMIT 1
            """)
    AccountCancellationDO selectLatest(@Param("userId") long userId);

    @Select("""
            SELECT id, user_id, apply_at, effective_at, completed_at,
                   status, revoked_at, version, create_time, update_time
              FROM auth_account_cancellation
             WHERE id = #{cancellationId}
               AND user_id = #{userId}
               FOR UPDATE
            """)
    AccountCancellationDO selectForUpdate(
            @Param("cancellationId") long cancellationId,
            @Param("userId") long userId);

    @Select("""
            SELECT id, user_id, apply_at, effective_at, completed_at,
                   status, revoked_at, version, create_time, update_time
              FROM auth_account_cancellation
             WHERE id = #{cancellationId}
            """)
    AccountCancellationDO selectById(@Param("cancellationId") long cancellationId);

    @Insert("""
            INSERT INTO auth_account_cancellation (
                id, user_id, apply_at, effective_at, completed_at,
                status, revoked_at, version, create_time, update_time
            ) VALUES (
                #{id}, #{userId}, #{applyAt}, #{effectiveAt}, NULL,
                'PENDING', NULL, 1, #{applyAt}, #{applyAt}
            )
            """)
    int insert(AccountCancellationDO cancellation);

    @Update("""
            UPDATE auth_account_cancellation
               SET status = 'REVOKED',
                   revoked_at = #{revokedAt},
                   version = version + 1,
                   update_time = #{revokedAt}
             WHERE id = #{cancellationId}
               AND user_id = #{userId}
               AND status = 'PENDING'
               AND version = #{expectedVersion}
            """)
    int revoke(
            @Param("cancellationId") long cancellationId,
            @Param("userId") long userId,
            @Param("expectedVersion") long expectedVersion,
            @Param("revokedAt") LocalDateTime revokedAt);

    @Select("""
            SELECT id, user_id, apply_at, effective_at, completed_at,
                   status, revoked_at, version, create_time, update_time
              FROM auth_account_cancellation
             WHERE (status = 'PENDING' AND effective_at <= #{now})
                OR status = 'EXECUTING'
             ORDER BY CASE WHEN status = 'EXECUTING' THEN 0 ELSE 1 END,
                      effective_at, id
             LIMIT #{limit}
            """)
    List<AccountCancellationDO> selectExecutable(
            @Param("now") LocalDateTime now,
            @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*)
              FROM auth_account_cancellation
             WHERE status = #{status}
            """)
    long countByStatus(@Param("status") String status);

    @Select("""
            SELECT MIN(apply_at)
              FROM auth_account_cancellation
             WHERE status IN ('PENDING', 'EXECUTING')
            """)
    LocalDateTime selectOldestOpenApplyAt();

    @Update("""
            UPDATE auth_account_cancellation
               SET status = 'EXECUTING',
                   version = version + 1,
                   update_time = #{now}
             WHERE id = #{cancellationId}
               AND user_id = #{userId}
               AND status = 'PENDING'
               AND version = #{expectedVersion}
               AND effective_at <= #{now}
            """)
    int claimExecution(
            @Param("cancellationId") long cancellationId,
            @Param("userId") long userId,
            @Param("expectedVersion") long expectedVersion,
            @Param("now") LocalDateTime now);

    @Update("""
            UPDATE auth_user
               SET status = 'CANCELLATION_EXECUTING',
                   update_time = #{now}
             WHERE id = #{userId}
               AND status = 'ACTIVE'
            """)
    int markUserExecuting(
            @Param("userId") long userId,
            @Param("now") LocalDateTime now);

    @Delete("DELETE FROM auth_password_credential WHERE user_id = #{userId}")
    int deletePasswordCredential(@Param("userId") long userId);

    @Delete("DELETE FROM auth_external_identity WHERE user_id = #{userId}")
    int deleteExternalIdentities(@Param("userId") long userId);

    @Delete("DELETE FROM auth_login_audit WHERE user_id = #{userId}")
    int deleteLoginAudits(@Param("userId") long userId);

    @Delete("DELETE FROM auth_idempotency_record WHERE user_id = #{userId}")
    int deleteIdempotency(@Param("userId") long userId);

    @Delete("DELETE FROM auth_user WHERE id = #{userId} AND status = 'CANCELLATION_EXECUTING'")
    int deleteExecutingUser(@Param("userId") long userId);

    @Update("""
            UPDATE auth_account_cancellation
               SET status = 'COMPLETED',
                   completed_at = #{completedAt},
                   version = version + 1,
                   update_time = #{completedAt}
             WHERE id = #{cancellationId}
               AND user_id = #{userId}
               AND status = 'EXECUTING'
               AND version = #{expectedVersion}
            """)
    int complete(
            @Param("cancellationId") long cancellationId,
            @Param("userId") long userId,
            @Param("expectedVersion") long expectedVersion,
            @Param("completedAt") LocalDateTime completedAt);

    @Delete("""
            DELETE FROM auth_account_cancellation
             WHERE status IN ('COMPLETED', 'REVOKED')
               AND update_time < #{cutoff}
             ORDER BY update_time, id
             LIMIT #{limit}
            """)
    int deleteTerminalBefore(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("limit") int limit);
}
