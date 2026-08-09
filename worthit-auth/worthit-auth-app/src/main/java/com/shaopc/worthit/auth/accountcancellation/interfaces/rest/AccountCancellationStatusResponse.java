package com.shaopc.worthit.auth.accountcancellation.interfaces.rest;

/** 当前用户最新账号注销记录；从未申请时 cancellation 为 null。 */
public record AccountCancellationStatusResponse(
        AccountCancellationResponse cancellation) {
}
