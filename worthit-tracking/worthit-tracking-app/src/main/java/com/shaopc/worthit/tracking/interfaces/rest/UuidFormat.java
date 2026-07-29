package com.shaopc.worthit.tracking.interfaces.rest;

import java.util.regex.Pattern;

/**
 * Tracking 公网 UUID 文本协议。
 */
public final class UuidFormat {

    /**
     * RFC 4122 版本 1 至 5、标准变体的 UUID 文本格式。
     */
    public static final String PATTERN =
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-"
                    + "[1-5][0-9a-fA-F]{3}-"
                    + "[89abAB][0-9a-fA-F]{3}-"
                    + "[0-9a-fA-F]{12}";

    private static final Pattern COMPILED_PATTERN =
            Pattern.compile(PATTERN);

    private UuidFormat() {
    }

    /**
     * 判断文本是否符合公网 UUID 协议。
     */
    public static boolean isValid(String value) {
        return value != null
                && COMPILED_PATTERN.matcher(value).matches();
    }
}
