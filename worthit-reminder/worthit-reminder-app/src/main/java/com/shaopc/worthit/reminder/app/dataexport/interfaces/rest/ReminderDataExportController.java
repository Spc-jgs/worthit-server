package com.shaopc.worthit.reminder.app.dataexport.interfaces.rest;

import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.security.error.SecurityErrorCode;
import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import com.shaopc.worthit.common.web.error.CommonWebErrorCode;
import com.shaopc.worthit.reminder.app.dataexport.application.ReminderDataExportService;
import com.shaopc.worthit.reminder.client.response.ReminderDataExportResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 仅供 Auth 在 Same-Token 可信边界内调用的 Reminder 导出接口。
 */
@RestController
@Tag(name = "内部数据导出", description = "Auth 数据导出编排使用")
public class ReminderDataExportController {

    private static final String AUTH_CALLER = "worthit-auth";

    private final ReminderDataExportService service;

    public ReminderDataExportController(ReminderDataExportService service) {
        this.service = service;
    }

    /**
     * 返回指定 Auth 用户的 Reminder 原始 JSON 分片。
     */
    @GetMapping("/internal/v1/reminders/users/{userId}/data-export")
    @Operation(summary = "导出用户 Reminder 数据")
    public ReminderDataExportResponse exportUserData(
            @PathVariable("userId") String userId,
            @RequestHeader(SecurityHeaderNames.CALLER_SERVICE) String callerService) {
        if (!AUTH_CALLER.equals(callerService)) {
            throw new BusinessException(
                    SecurityErrorCode.AUTH_FORBIDDEN,
                    "当前内部调用方无权导出 Reminder 数据");
        }
        return service.exportUserData(parsePositiveLong(userId));
    }

    private static long parsePositiveLong(String value) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed > 0) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
            // 统一转换为稳定参数错误。
        }
        throw new BusinessException(
                CommonWebErrorCode.VAL_INVALID_ARGUMENT,
                "标识必须是可表示的正整数");
    }
}
