package com.shaopc.worthit.tracking.subscription.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shaopc.worthit.tracking.subscription.domain.Subscription;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Subscription 表 Mapper 与用户隔离查询。
 */
@Mapper
public interface SubscriptionMapper
        extends BaseMapper<SubscriptionDO> {

    @Select("""
            SELECT s.id, s.user_id, s.category_id,
                   c.name AS category_name, s.name,
                   s.amount, s.currency,
                   s.billing_cycle_type,
                   s.billing_cycle_value,
                   s.cny_reference_amount,
                   s.next_renewal_date, s.auto_renew,
                   s.renewal_reminder_enabled,
                   s.status, s.remark, s.version,
                   s.create_time, s.update_time
            FROM trk_subscription s
            JOIN trk_category c
              ON c.id = s.category_id
             AND c.user_id = s.user_id
             AND c.del_flag = 0
            WHERE s.id = #{subscriptionId}
              AND s.user_id = #{userId}
              AND s.del_flag = 0
            """)
    SubscriptionViewDO selectDetail(
            @Param("subscriptionId") long subscriptionId,
            @Param("userId") long userId);

    @Select("""
            <script>
            SELECT s.id, s.user_id, s.category_id,
                   c.name AS category_name, s.name,
                   s.amount, s.currency,
                   s.billing_cycle_type,
                   s.billing_cycle_value,
                   s.cny_reference_amount,
                   s.next_renewal_date, s.auto_renew,
                   s.renewal_reminder_enabled,
                   s.status, s.remark, s.version,
                   s.create_time, s.update_time
            FROM trk_subscription s
            JOIN trk_category c
              ON c.id = s.category_id
             AND c.user_id = s.user_id
             AND c.del_flag = 0
            WHERE s.user_id = #{userId}
              AND s.del_flag = 0
            <if test="keyword != null">
              AND s.name LIKE CONCAT('%', #{keyword}, '%')
            </if>
            <if test="categoryId != null">
              AND s.category_id = #{categoryId}
            </if>
            ORDER BY s.create_time DESC, s.id DESC
            LIMIT #{offset}, #{size}
            </script>
            """)
    List<SubscriptionViewDO> selectPage(
            @Param("userId") long userId,
            @Param("keyword") String keyword,
            @Param("categoryId") Long categoryId,
            @Param("offset") long offset,
            @Param("size") int size);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM trk_subscription s
            JOIN trk_category c
              ON c.id = s.category_id
             AND c.user_id = s.user_id
             AND c.del_flag = 0
            WHERE s.user_id = #{userId}
              AND s.del_flag = 0
            <if test="keyword != null">
              AND s.name LIKE CONCAT('%', #{keyword}, '%')
            </if>
            <if test="categoryId != null">
              AND s.category_id = #{categoryId}
            </if>
            </script>
            """)
    long countPage(
            @Param("userId") long userId,
            @Param("keyword") String keyword,
            @Param("categoryId") Long categoryId);

    @Update("""
            UPDATE trk_subscription
            SET category_id = #{subscription.categoryId},
                name = #{subscription.name},
                amount = #{subscription.amount},
                currency = #{subscription.currency},
                billing_cycle_type =
                    #{subscription.billingCycleType},
                billing_cycle_value =
                    #{subscription.billingCycleValue},
                cny_reference_amount =
                    #{subscription.cnyReferenceAmount},
                next_renewal_date =
                    #{subscription.nextRenewalDate},
                auto_renew = #{subscription.autoRenew},
                renewal_reminder_enabled =
                    #{subscription.renewalReminderEnabled},
                remark = #{subscription.remark},
                version = version + 1,
                update_by = #{subscription.userId},
                update_time = #{subscription.updateTime}
            WHERE id = #{subscription.id}
              AND user_id = #{subscription.userId}
              AND version = #{expectedVersion}
              AND del_flag = 0
            """)
    int updateByVersion(
            @Param("subscription")
            Subscription subscription,
            @Param("expectedVersion")
            long expectedVersion);

    @Update("""
            UPDATE trk_subscription
            SET status = #{targetStatus},
                version = version + 1,
                update_by = #{userId},
                update_time = #{now}
            WHERE id = #{subscriptionId}
              AND user_id = #{userId}
              AND version = #{expectedVersion}
              AND status = #{expectedStatus}
              AND del_flag = 0
            """)
    int changeStatus(
            @Param("subscriptionId") long subscriptionId,
            @Param("userId") long userId,
            @Param("expectedVersion") long expectedVersion,
            @Param("expectedStatus") String expectedStatus,
            @Param("targetStatus") String targetStatus,
            @Param("now") LocalDateTime now);

    @Update("""
            UPDATE trk_subscription
            SET next_renewal_date =
                    #{subscription.nextRenewalDate},
                renewal_reminder_enabled =
                    #{subscription.renewalReminderEnabled},
                status = 'ACTIVE',
                version = version + 1,
                update_by = #{subscription.userId},
                update_time = #{subscription.updateTime}
            WHERE id = #{subscription.id}
              AND user_id = #{subscription.userId}
              AND version = #{expectedVersion}
              AND status = #{expectedStatus}
              AND del_flag = 0
            """)
    int resume(
            @Param("subscription")
            Subscription subscription,
            @Param("expectedVersion")
            long expectedVersion,
            @Param("expectedStatus")
            String expectedStatus);

    @Update("""
            UPDATE trk_subscription
            SET del_flag = 1,
                delete_time = #{now},
                version = version + 1,
                update_by = #{userId},
                update_time = #{now}
            WHERE id = #{subscriptionId}
              AND user_id = #{userId}
              AND version = #{expectedVersion}
              AND del_flag = 0
            """)
    int deleteByVersion(
            @Param("subscriptionId") long subscriptionId,
            @Param("userId") long userId,
            @Param("expectedVersion") long expectedVersion,
            @Param("now") LocalDateTime now);

    @Update("""
            UPDATE trk_subscription
            SET del_flag = 0,
                delete_time = NULL,
                version = version + 1,
                update_by = #{userId},
                update_time = #{now}
            WHERE id = #{subscriptionId}
              AND user_id = #{userId}
              AND version = #{deletedVersion}
              AND del_flag = 1
            """)
    int restoreByVersion(
            @Param("subscriptionId") long subscriptionId,
            @Param("userId") long userId,
            @Param("deletedVersion") long deletedVersion,
            @Param("now") LocalDateTime now);
}
