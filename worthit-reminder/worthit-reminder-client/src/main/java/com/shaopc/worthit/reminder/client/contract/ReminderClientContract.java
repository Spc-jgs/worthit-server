package com.shaopc.worthit.reminder.client.contract;

/**
 * Reminder 内部 Client 的稳定协议常量。
 *
 * <p>常量由接口终稿 V0.1.2 冻结，调用方不得自行拼接路径或改写请求头。</p>
 */
public final class ReminderClientContract {

    /**
     * Reminder 内部接口公共路径。
     */
    public static final String BASE_PATH = "/internal/v1/reminders";

    /**
     * Reminder reconcile 接口相对路径。
     */
    public static final String RECONCILE_PATH = "/reconcile";

    /**
     * Outbox 事件标识使用的内部幂等请求头。
     */
    public static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";

    /**
     * M1 Reminder reconcile 契约版本。
     */
    public static final int SCHEMA_VERSION = 1;

    private ReminderClientContract() {
    }
}
