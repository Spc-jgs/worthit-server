package com.shaopc.worthit.tracking.idempotency.infrastructure;

/**
 * 幂等记录持久化状态。
 */
enum IdempotencyRecordStatus {

    /** 请求处理中。 */
    PROCESSING("PROCESSING"),
    /** 请求已成功，可重放响应。 */
    SUCCEEDED("SUCCEEDED"),
    /** 终结性业务失败，可重放安全错误。 */
    FAILED("FAILED");

    private final String code;

    IdempotencyRecordStatus(String code) {
        this.code = code;
    }

    String code() {
        return code;
    }

    static IdempotencyRecordStatus fromCode(String code) {
        for (IdempotencyRecordStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException(
                "不支持的幂等记录状态: " + code);
    }
}
