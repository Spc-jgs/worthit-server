package com.shaopc.worthit.tracking.dataexport.application;

import com.shaopc.worthit.tracking.client.response.TrackingDataExportResponse;

/** Tracking 按数据所有权生成用户导出分片的应用用例。 */
public interface TrackingDataExportService {

    /**
     * 在 Tracking 本地一致快照中导出指定用户的全部基础业务数据。
     *
     * @param userId Auth 已认证的内部用户标识
     * @return 完整 Tracking 分片
     */
    TrackingDataExportResponse exportUserData(long userId);
}
