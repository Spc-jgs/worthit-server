package com.shaopc.worthit.auth.dataexport.application;

/** 当前登录用户的基础数据导出用例。 */
public interface AuthDataExportService {

    /**
     * 聚合本人在三个服务中的基础数据并生成完整 ZIP。
     *
     * @return 可直接返回的完整归档
     */
    DataExportArchive exportCurrentUserData();
}
