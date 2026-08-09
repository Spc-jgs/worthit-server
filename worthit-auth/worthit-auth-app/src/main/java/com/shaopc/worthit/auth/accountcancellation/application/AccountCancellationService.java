package com.shaopc.worthit.auth.accountcancellation.application;

import com.shaopc.worthit.auth.accountcancellation.interfaces.rest.AccountCancellationResponse;
import com.shaopc.worthit.auth.accountcancellation.interfaces.rest.AccountCancellationStatusResponse;

/** 当前用户账号注销申请、状态查询与撤销用例。 */
public interface AccountCancellationService {

    AccountCancellationResponse apply(String idempotencyKey);

    AccountCancellationStatusResponse status();

    AccountCancellationResponse revoke(
            String idempotencyKey, String cancellationId, long version);
}
