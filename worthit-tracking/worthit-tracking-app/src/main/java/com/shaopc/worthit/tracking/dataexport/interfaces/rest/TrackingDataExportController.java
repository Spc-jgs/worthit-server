package com.shaopc.worthit.tracking.dataexport.interfaces.rest;

import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.security.error.SecurityErrorCode;
import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import com.shaopc.worthit.tracking.client.response.TrackingDataExportResponse;
import com.shaopc.worthit.tracking.dataexport.application.TrackingDataExportService;
import com.shaopc.worthit.tracking.interfaces.rest.PositiveLongIdParser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * 仅供 Auth 在 Same-Token 可信边界内调用的 Tracking 导出接口。
 */
@RestController
@Tag(name = "内部数据导出", description = "Auth 数据导出编排使用")
public class TrackingDataExportController {

    private static final String AUTH_CALLER = "worthit-auth";

    private final TrackingDataExportService service;

    public TrackingDataExportController(TrackingDataExportService service) {
        this.service = service;
    }

    /**
     * 返回指定 Auth 用户的 Tracking 原始 JSON 分片。
     */
    @GetMapping("/internal/v1/tracking/users/{userId}/data-export")
    @Operation(summary = "导出用户 Tracking 数据")
    public TrackingDataExportResponse exportUserData(
            @PathVariable("userId") String userId,
            @RequestHeader(SecurityHeaderNames.CALLER_SERVICE) String callerService) {
        if (!AUTH_CALLER.equals(callerService)) {
            throw new BusinessException(
                    SecurityErrorCode.AUTH_FORBIDDEN,
                    "当前内部调用方无权导出 Tracking 数据");
        }
        long parsedUserId = Objects.requireNonNull(
                PositiveLongIdParser.parseNullable(userId));
        return service.exportUserData(parsedUserId);
    }
}
