package com.shaopc.worthit.tracking.interfaces.rest;

import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.web.error.CommonWebErrorCode;

/**
 * 将跨端字符串标识解析为 Java 正 {@code long} 的 Tracking Web 边界。
 */
public final class PositiveLongIdParser {

    /** 公网正 long 标识的文本格式。 */
    public static final String PATTERN = "[1-9]\\d{0,18}";

    private PositiveLongIdParser() {
    }

    /**
     * 解析可空的正数标识。
     *
     * @param value 跨端字符串标识
     * @return 空值或可由 Java {@code long} 表示的正数
     * @throws BusinessException 格式错误、溢出或非正数时抛出
     */
    public static Long parseNullable(String value) {
        if (value == null) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw invalidId();
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw invalidId();
        }
    }

    private static BusinessException invalidId() {
        return new BusinessException(
                CommonWebErrorCode.VAL_INVALID_ARGUMENT,
                "标识必须是可表示的正整数");
    }
}
