package com.shaopc.worthit.auth.accountcancellation.application.idempotency;

/** Auth 账号注销公网写操作码。 */
public enum AuthCancellationOperation {
    APPLY("AUTH_ACCOUNT_CANCELLATION_APPLY"),
    REVOKE("AUTH_ACCOUNT_CANCELLATION_REVOKE");

    private final String code;

    AuthCancellationOperation(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
