package com.shaopc.worthit.tracking.outbox.application;

/**
 * Tracking Outbox 事件类型。
 */
public enum OutboxEventType {

    /** 按聚合期望态对账提醒。 */
    REMINDER_RECONCILE("REMINDER_RECONCILE");

    private final String code;

    OutboxEventType(String code) {
        this.code = code;
    }

    /**
     * 返回持久化使用的稳定事件类型。
     */
    public String code() {
        return code;
    }

    /**
     * 从持久化编码恢复事件类型。
     */
    public static OutboxEventType fromCode(String code) {
        for (OutboxEventType eventType : values()) {
            if (eventType.code.equals(code)) {
                return eventType;
            }
        }
        throw new IllegalArgumentException(
                "不支持的Outbox事件类型: " + code);
    }
}
