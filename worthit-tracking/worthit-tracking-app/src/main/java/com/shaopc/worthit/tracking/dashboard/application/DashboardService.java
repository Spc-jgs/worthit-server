package com.shaopc.worthit.tracking.dashboard.application;

/**
 * Dashboard 汇总公开应用用例。
 */
public interface DashboardService {

    /**
     * 汇总当前用户的 M1 Dashboard 指标。
     */
    DashboardResult summary();
}
