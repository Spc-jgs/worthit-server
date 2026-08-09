package com.shaopc.worthit.auth.accountcancellation.application;

/** 到期账号注销的可重试跨服务编排用例。 */
public interface AccountCancellationExecutionService {

    /** 扫描并处理一批到期或执行中的注销记录。 */
    void processBatch();
}
