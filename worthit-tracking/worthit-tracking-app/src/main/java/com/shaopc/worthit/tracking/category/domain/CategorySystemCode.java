package com.shaopc.worthit.tracking.category.domain;

/**
 * 系统分类编码。
 */
public enum CategorySystemCode {

    /** 无法归入用户自定义分类的数据。 */
    UNCATEGORIZED("UNCATEGORIZED");

    private final String code;

    CategorySystemCode(String code) {
        this.code = code;
    }

    /**
     * 返回持久化与公网契约使用的稳定编码。
     */
    public String code() {
        return code;
    }

    /**
     * 从持久化编码恢复系统分类。
     *
     * @throws IllegalArgumentException 编码为空或不受支持
     */
    public static CategorySystemCode fromCode(String code) {
        for (CategorySystemCode systemCode : values()) {
            if (systemCode.code.equals(code)) {
                return systemCode;
            }
        }
        throw new IllegalArgumentException(
                "不支持的系统分类编码: " + code);
    }
}
