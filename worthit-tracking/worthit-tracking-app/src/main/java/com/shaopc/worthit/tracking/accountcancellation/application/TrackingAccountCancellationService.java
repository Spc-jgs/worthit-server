package com.shaopc.worthit.tracking.accountcancellation.application;

import com.shaopc.worthit.tracking.client.response.TrackingAccountCancellationResponse;

/** Tracking 账号注销本地清理用例。 */
public interface TrackingAccountCancellationService {

    /** 幂等清理指定用户全部 Tracking 业务与技术数据。 */
    TrackingAccountCancellationResponse cancel(long userId, String cancellationId);
}
