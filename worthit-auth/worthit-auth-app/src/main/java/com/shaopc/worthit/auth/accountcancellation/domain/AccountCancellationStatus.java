package com.shaopc.worthit.auth.accountcancellation.domain;

/** Auth 账号注销持久状态。 */
public enum AccountCancellationStatus {
    PENDING,
    EXECUTING,
    COMPLETED,
    REVOKED
}
