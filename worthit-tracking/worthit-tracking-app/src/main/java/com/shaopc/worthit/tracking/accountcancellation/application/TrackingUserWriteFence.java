package com.shaopc.worthit.tracking.accountcancellation.application;

/** Tracking 写事务进入业务修改前必须持有的用户级围栏。 */
public interface TrackingUserWriteFence {

    /** 懒建并锁定用户围栏，只有 ACTIVE 状态允许继续。 */
    void requireActive(long userId);
}
