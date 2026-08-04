package com.shaopc.worthit.auth.dataexport.application;

import com.shaopc.worthit.reminder.client.response.ReminderDataExportResponse;
import com.shaopc.worthit.tracking.client.response.TrackingDataExportResponse;

import java.time.Instant;

/** 将三个服务已完成的数据快照组装成稳定归档。 */
@FunctionalInterface
public interface DataExportArchiveAssembler {

    /** 生成最终 ZIP；任何失败都不得返回部分字节。 */
    DataExportArchive assemble(
            long userId,
            Instant exportedAt,
            AuthDataExportAccount account,
            TrackingDataExportResponse tracking,
            ReminderDataExportResponse reminder);
}
