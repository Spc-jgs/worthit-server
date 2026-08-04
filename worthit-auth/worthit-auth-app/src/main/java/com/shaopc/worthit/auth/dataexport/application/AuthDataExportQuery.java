package com.shaopc.worthit.auth.dataexport.application;

/** Auth 账号导出的只读查询端口。 */
@FunctionalInterface
public interface AuthDataExportQuery {

    /**
     * 查询指定用户的可导出账号字段。
     *
     * @param userId 当前登录用户标识
     * @return 账号导出分片
     */
    AuthDataExportAccount exportAccount(long userId);
}
