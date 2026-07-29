package com.shaopc.worthit.tracking.restore.application;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Tracking 业务对象的统一短时恢复窗口。
 */
@Component
public class RestoreWindowPolicy {

    private static final Duration WINDOW = Duration.ofSeconds(60);

    /**
     * 计算删除记录的恢复截止时间。
     *
     * @param deletedAt 删除时间
     * @return 包含在恢复窗口内的截止时间
     */
    public LocalDateTime deadlineFrom(LocalDateTime deletedAt) {
        return deletedAt.plus(WINDOW);
    }

    /**
     * 计算指定时刻仍可恢复记录的最早删除时间。
     *
     * @param now 当前时间
     * @return 包含在恢复窗口内的删除时间下界
     */
    public LocalDateTime earliestRestorableDeletion(
            LocalDateTime now) {
        return now.minus(WINDOW);
    }

    /**
     * 判断删除记录在指定时刻是否仍可恢复。
     *
     * @param deletedAt 删除时间
     * @param now 当前时间
     * @return 精确截止时刻仍返回 true
     */
    public boolean isRestorable(
            LocalDateTime deletedAt, LocalDateTime now) {
        return !now.isAfter(deadlineFrom(deletedAt));
    }
}
