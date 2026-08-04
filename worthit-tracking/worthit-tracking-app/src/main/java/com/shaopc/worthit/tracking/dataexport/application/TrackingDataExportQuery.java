package com.shaopc.worthit.tracking.dataexport.application;

import com.shaopc.worthit.tracking.client.response.TrackingDataExportResponse;

import java.util.List;

/**
 * 按用户、主键升序读取导出记录的应用端口。
 */
public interface TrackingDataExportQuery {

    List<TrackingDataExportResponse.Category> categories(long userId, int limit);

    List<TrackingDataExportResponse.Item> items(long userId, int limit);

    List<TrackingDataExportResponse.Subscription> subscriptions(long userId, int limit);

    List<TrackingDataExportResponse.Wish> wishes(long userId, int limit);

    List<TrackingDataExportResponse.Disposal> disposals(long userId, int limit);

    List<TrackingDataExportResponse.Replacement> replacements(long userId, int limit);
}
