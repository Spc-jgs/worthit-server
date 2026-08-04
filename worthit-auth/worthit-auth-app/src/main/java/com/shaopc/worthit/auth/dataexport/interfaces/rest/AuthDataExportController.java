package com.shaopc.worthit.auth.dataexport.interfaces.rest;

import com.shaopc.worthit.auth.dataexport.application.AuthDataExportService;
import com.shaopc.worthit.auth.dataexport.application.DataExportArchive;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 当前登录用户的公网基础数据导出入口。 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "数据导出", description = "导出当前登录用户的基础数据")
@RequiredArgsConstructor
public class AuthDataExportController {

    private static final MediaType ZIP_MEDIA_TYPE =
            MediaType.parseMediaType("application/zip");

    private final AuthDataExportService dataExportService;

    /** 返回响应提交前已完整生成的 ZIP。 */
    @GetMapping(value = "/data-export", produces = "application/zip")
    @Operation(
            summary = "导出本人基础数据",
            responses = @ApiResponse(
                    responseCode = "200",
                    content = @Content(
                            mediaType = "application/zip",
                            schema = @Schema(type = "string", format = "binary"))))
    public ResponseEntity<byte[]> exportCurrentUserData() {
        DataExportArchive archive =
                dataExportService.exportCurrentUserData();
        byte[] content = archive.content();
        return ResponseEntity.ok()
                .contentType(ZIP_MEDIA_TYPE)
                .contentLength(content.length)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(archive.fileName())
                                .build()
                                .toString())
                .header(
                        HttpHeaders.CACHE_CONTROL,
                        CacheControl.noStore().getHeaderValue())
                .body(content);
    }
}
