package com.shaopc.worthit.common.webmvc.error;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.core.error.ErrorCode;
import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorthItRestExceptionHandlerTest {

    private LocalValidatorFactoryBean validator;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        WorthItRestExceptionHandler handler =
                new WorthItRestExceptionHandler(
                        new DefaultErrorHttpStatusResolver(),
                        () -> "trace-generated");
        mockMvc = MockMvcBuilders
                .standaloneSetup(new FailureController())
                .setControllerAdvice(handler)
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void tearDown() {
        validator.close();
    }

    @Test
    void mapsBusinessExceptionWithDomainCodeAndCurrentTraceId()
            throws Exception {
        mockMvc.perform(get("/fail/business")
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID,
                                "trace-request"))
                .andExpect(status().isConflict())
                .andExpect(header().string(
                        SecurityHeaderNames.TRACE_ID,
                        "trace-request"))
                .andExpect(jsonPath("$.code").value("BIZ_CONFLICT"))
                .andExpect(jsonPath("$.message").value("测试业务冲突"))
                .andExpect(jsonPath("$.traceId").value("trace-request"));
    }

    @Test
    void mapsBeanValidationToStructuredFieldDetails() throws Exception {
        mockMvc.perform(post("/fail/validation")
                        .contentType("application/json")
                        .content("""
                                {"name":""}
                                """)
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID,
                                "trace-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VAL_INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.details[0].field").value("name"))
                .andExpect(jsonPath("$.details[0].issue").value("不能为空"))
                .andExpect(jsonPath("$.traceId").value("trace-request"));
    }

    @Test
    void mapsMalformedJsonWithoutLeakingParserDetails() throws Exception {
        mockMvc.perform(post("/fail/validation")
                        .contentType("application/json")
                        .content("{")
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID,
                                "trace-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VAL_INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.details[0].field")
                        .value("requestBody"))
                .andExpect(jsonPath("$.details[0].issue")
                        .value("请求体格式不正确"))
                .andExpect(jsonPath("$.traceId").value("trace-request"));
    }

    @Test
    void mapsRequestParameterTypeMismatchToValidationError()
            throws Exception {
        mockMvc.perform(get("/fail/type")
                        .param("count", "not-a-number")
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID,
                                "trace-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VAL_INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.details[0].field").value("count"))
                .andExpect(jsonPath("$.details[0].issue")
                        .value("参数类型不正确"));
    }

    @Test
    void mapsHandlerMethodValidationToStructuredFieldDetails()
            throws Exception {
        mockMvc.perform(get("/fail/method-validation")
                        .param("size", "0")
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID,
                                "trace-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VAL_INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.details[0].field").value("size"))
                .andExpect(jsonPath("$.details[0].issue")
                        .value("必须大于等于1"));
    }

    @Test
    void mapsSaTokenAuthenticationAndAuthorizationFailures()
            throws Exception {
        mockMvc.perform(get("/fail/not-login"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"))
                .andExpect(jsonPath("$.traceId").value("trace-generated"));

        mockMvc.perform(get("/fail/not-permitted"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"))
                .andExpect(jsonPath("$.traceId").value("trace-generated"));
    }

    @Test
    void mapsMissingResourceToStableNotFoundResponse() throws Exception {
        mockMvc.perform(get("/fail/not-found")
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID,
                                "trace-request"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RES_NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").value("trace-request"));
    }

    @Test
    void hidesUnknownInternalExceptionMessage() throws Exception {
        mockMvc.perform(get("/fail/unknown")
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID,
                                "trace-request"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string(
                        SecurityHeaderNames.TRACE_ID,
                        "trace-request"))
                .andExpect(jsonPath("$.code").value("SYS_ERROR"))
                .andExpect(jsonPath("$.message").value("系统错误"))
                .andExpect(jsonPath("$.traceId").value("trace-request"))
                .andExpect(jsonPath("$.message")
                        .value(not(containsString(
                                "sensitive-internal-message"))));
    }

    @RestController
    static class FailureController {

        @GetMapping("/fail/business")
        void business() {
            throw new BusinessException(TestErrorCode.BIZ_CONFLICT);
        }

        @PostMapping("/fail/validation")
        void validation(@Valid @RequestBody TestRequest request) {
        }

        @GetMapping("/fail/type")
        void type(@RequestParam("count") int count) {
        }

        @GetMapping("/fail/method-validation")
        void methodValidation(
                @RequestParam("size")
                @Min(value = 1, message = "必须大于等于1")
                int size) {
        }

        @GetMapping("/fail/not-login")
        void notLogin() {
            throw NotLoginException.newInstance(
                    "login",
                    NotLoginException.NOT_TOKEN,
                    NotLoginException.NOT_TOKEN_MESSAGE,
                    null);
        }

        @GetMapping("/fail/not-permitted")
        void notPermitted() {
            throw new NotPermissionException("item:read");
        }

        @GetMapping("/fail/not-found")
        void notFound() throws NoResourceFoundException {
            throw new NoResourceFoundException(
                    HttpMethod.GET,
                    "/missing");
        }

        @GetMapping("/fail/unknown")
        void unknown() {
            throw new IllegalStateException(
                    "sensitive-internal-message");
        }
    }

    record TestRequest(@NotBlank(message = "不能为空") String name) {
    }

    enum TestErrorCode implements ErrorCode {
        BIZ_CONFLICT;

        @Override
        public String code() {
            return name();
        }

        @Override
        public String defaultMessage() {
            return "测试业务冲突";
        }
    }
}
