package com.shaopc.worthit.reminder.app.reminder.interfaces.rest;

import com.shaopc.worthit.common.core.pagination.PageResult;
import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import com.shaopc.worthit.common.web.response.ApiResponse;
import com.shaopc.worthit.reminder.app.reminder.application.ReminderListItem;
import com.shaopc.worthit.reminder.app.reminder.application.ReminderTab;
import com.shaopc.worthit.reminder.app.reminder.application.ReminderViewService;
import com.shaopc.worthit.reminder.client.model.ReminderBusinessType;
import com.shaopc.worthit.reminder.client.model.ReminderType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Reminder 公网接口。
 */
@Validated
@RestController
@RequestMapping("/api/v1/reminders")
@Tag(name = "提醒", description = "提醒中心列表与忽略")
public class ReminderController {

    private final ReminderViewService reminderService;

    /**
     * 创建 Reminder 公网 Controller。
     */
    public ReminderController(
            ReminderViewService reminderService) {
        this.reminderService = reminderService;
    }

    /**
     * 分页查询待处理或已处理提醒。
     */
    @GetMapping
    @Operation(summary = "分页查询提醒中心")
    public ApiResponse<ReminderPageResponse> list(
            @RequestParam("tab")
            ReminderTab tab,
            @RequestParam(
                    name = "page",
                    defaultValue = "1")
            @Min(value = 1, message = "页码不能小于1")
            int page,
            @RequestParam(
                    name = "size",
                    defaultValue = "20")
            @Min(value = 1, message = "每页条数不能小于1")
            @Max(value = 50, message = "每页条数不能大于50")
            int size,
            @RequestAttribute(SecurityHeaderNames.TRACE_ID)
            String traceId) {
        PageResult<ReminderListItem> result =
                reminderService.list(
                        tab,
                        page,
                        size);
        List<ReminderItemResponse> items = result
                .getItems()
                .stream()
                .map(this::toResponse)
                .toList();
        return ApiResponse.success(
                new ReminderPageResponse(
                        items,
                        result.getPage(),
                        result.getSize(),
                        result.getTotal(),
                        result.isHasMore()),
                traceId);
    }

    /**
     * 查询已到期待处理提醒数量。
     */
    @GetMapping("/pending-count")
    @Operation(summary = "查询待处理提醒数量")
    public ApiResponse<PendingReminderCountResponse>
            pendingCount(
                    @RequestAttribute(
                            SecurityHeaderNames.TRACE_ID)
                    String traceId) {
        return ApiResponse.success(
                new PendingReminderCountResponse(
                        reminderService.pendingCount()),
                traceId);
    }

    /**
     * 忽略本次已到期提醒。
     */
    @PostMapping("/{id}/ignore")
    @Operation(summary = "忽略本次提醒")
    public ApiResponse<Void> ignore(
            @Positive
            @PathVariable("id")
            long reminderId,
            @RequestAttribute(SecurityHeaderNames.TRACE_ID)
            String traceId) {
        reminderService.ignore(reminderId);
        return ApiResponse.success(null, traceId);
    }

    private ReminderItemResponse toResponse(
            ReminderListItem item) {
        return new ReminderItemResponse(
                Long.toString(item.id()),
                item.reminderType().name(),
                item.businessType().name(),
                Long.toString(item.businessId()),
                null,
                item.businessDate(),
                item.remindAt(),
                item.status(),
                title(item.reminderType()),
                detailPath(
                        item.businessType(),
                        item.businessId()));
    }

    private static String title(
            ReminderType reminderType) {
        return switch (reminderType) {
            case RENEWAL -> "续费提醒";
            case WARRANTY -> "保修提醒";
            case WATCH -> "观望提醒";
        };
    }

    private static String detailPath(
            ReminderBusinessType businessType,
            long businessId) {
        String resource = switch (businessType) {
            case ITEM -> "items";
            case SUBSCRIPTION -> "subscriptions";
            case WISH -> "wishes";
        };
        return "/" + resource + "/" + businessId;
    }
}
