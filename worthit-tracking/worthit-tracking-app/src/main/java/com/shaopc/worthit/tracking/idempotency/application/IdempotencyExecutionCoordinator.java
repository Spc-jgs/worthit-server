package com.shaopc.worthit.tracking.idempotency.application;

/**
 * 编排持久化 claim、业务事务和终结性失败重放。
 */
public interface IdempotencyExecutionCoordinator {

    /**
     * 在幂等状态机保护下执行一次业务命令。
     *
     * @param userId 当前用户
     * @param operation 服务端冻结的操作码
     * @param idempotencyKey 客户端幂等键
     * @param requestHash 规范化请求摘要
     * @param responseType 成功响应类型
     * @param action 需要在本地业务事务中执行的动作
     * @param <T> 成功响应类型
     * @return 首次执行或重放的成功响应
     */
    <T> T execute(
            long userId,
            TrackingOperation operation,
            String idempotencyKey,
            String requestHash,
            Class<T> responseType,
            IdempotentAction<T> action);

    /**
     * 幂等协调器包裹的业务动作。
     */
    @FunctionalInterface
    interface IdempotentAction<T> {

        /**
         * 执行业务事务。
         */
        T execute();
    }
}
