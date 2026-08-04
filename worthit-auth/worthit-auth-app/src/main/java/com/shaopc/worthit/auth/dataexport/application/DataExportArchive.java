package com.shaopc.worthit.auth.dataexport.application;

import java.util.Objects;

/** 已在内存中完整生成且可一次性写入 HTTP 响应的数据导出归档。 */
public record DataExportArchive(String fileName, byte[] content) {

    /** 校验完整归档元数据。 */
    public DataExportArchive {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("导出文件名不能为空");
        }
        Objects.requireNonNull(content, "导出归档不能为空");
        if (content.length == 0) {
            throw new IllegalArgumentException("导出归档不能为空");
        }
    }
}
