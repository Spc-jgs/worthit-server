package com.shaopc.worthit.reminder.client.model;

/**
 * Tracking 服务端生成的 Reminder 业务操作类型。
 */
public enum ReminderOperationType {

    /**
     * 首次同步完整期望状态。
     */
    INITIAL_SYNC,

    /**
     * 开启提醒。
     */
    ENABLE_REMINDER,

    /**
     * 关闭提醒。
     */
    DISABLE_REMINDER,

    /**
     * 更新业务日期。
     */
    UPDATE_BUSINESS_DATE,

    /**
     * 推进下一续费日期。
     */
    ADVANCE_NEXT_RENEWAL_DATE,

    /**
     * 修正业务日期。
     */
    CORRECT_BUSINESS_DATE,

    /**
     * 暂停订阅。
     */
    PAUSE_SUBSCRIPTION,

    /**
     * 结束订阅。
     */
    END_SUBSCRIPTION,

    /**
     * 恢复订阅。
     */
    RESUME_SUBSCRIPTION,

    /**
     * 购买想买对象。
     */
    PURCHASE_WISH,

    /**
     * 放弃想买对象。
     */
    ABANDON_WISH,

    /**
     * 继续考虑想买对象。
     */
    CONTINUE_CONSIDERING,

    /**
     * 处置物品。
     */
    DISPOSE_ITEM,

    /**
     * 删除业务对象。
     */
    DELETE_OBJECT
}
