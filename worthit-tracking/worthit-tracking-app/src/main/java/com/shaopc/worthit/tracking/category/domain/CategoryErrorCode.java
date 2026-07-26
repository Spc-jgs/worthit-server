package com.shaopc.worthit.tracking.category.domain;

import com.shaopc.worthit.common.core.error.ErrorCode;

/**
 * 分类业务稳定错误码。
 */
public enum CategoryErrorCode implements ErrorCode {

    /** 分类名称与当前有效分类重复。 */
    BIZ_CONFLICT("BIZ_CONFLICT", "分类名称已存在"),

    /** 系统内置分类不允许删除。 */
    BIZ_CATEGORY_SYSTEM_PROTECTED(
            "BIZ_CATEGORY_SYSTEM_PROTECTED",
            "系统分类不能删除"),

    /** 分类仍被有效业务数据引用。 */
    BIZ_CATEGORY_IN_USE(
            "BIZ_CATEGORY_IN_USE",
            "分类正在使用中，不能删除");

    private final String code;
    private final String defaultMessage;

    CategoryErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }
}
