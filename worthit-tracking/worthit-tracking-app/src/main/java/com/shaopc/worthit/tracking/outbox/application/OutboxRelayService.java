package com.shaopc.worthit.tracking.outbox.application;

/**
 * Tracking Outbox 投递公开应用用例。
 */
public interface OutboxRelayService {

    /**
     * 抢占并逐条投递一批到期事件。
     *
     * @return 本轮抢占事件数
     */
    int relayBatch();
}
