package com.shaopc.worthit.tracking.dashboard.interfaces.rest;

import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import com.shaopc.worthit.common.web.response.ApiResponse;
import com.shaopc.worthit.tracking.dashboard.application.DashboardResult;
import com.shaopc.worthit.tracking.dashboard.application.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 首页 Dashboard 公网接口。
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "首页", description = "Tracking 实时成本汇总")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * 查询当前用户的首页实时成本汇总。
     *
     * @param traceId Gateway 生成的可信链路标识
     * @return Dashboard 统一响应信封
     */
    @GetMapping
    @Operation(
            summary = "查询首页成本汇总",
            description = "只汇总Tracking事实，不包含提醒待处理数量")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "查询成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "登录态无效"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "500",
                description = "系统异常")
    })
    public ApiResponse<DashboardResponse> summary(
            @RequestAttribute(SecurityHeaderNames.TRACE_ID)
            String traceId) {
        return ApiResponse.success(
                toResponse(dashboardService.summary()),
                traceId);
    }

    private static DashboardResponse toResponse(
            DashboardResult result) {
        return new DashboardResponse(
                result.itemPlanDailyTotal(),
                result.itemPlanDailyTotalDisplay(),
                result.itemResidualUnsetCount(),
                result.subscriptionMonthlyCnyTotal(),
                result.subscriptionMonthlyCnyTotalDisplay(),
                result.subscriptionMonthlyCnyApproximate(),
                result.subscriptionUnconvertedForeignCount(),
                result.wishConsideringCount(),
                result.wishConsideringAmountTotal(),
                result.wishConsideringAmountTotalDisplay());
    }
}
