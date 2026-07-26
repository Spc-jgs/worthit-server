package com.shaopc.worthit.auth.authentication.interfaces;

import com.shaopc.worthit.auth.authentication.application.AuthenticationResult;
import com.shaopc.worthit.auth.authentication.application.AuthenticationService;
import com.shaopc.worthit.auth.authentication.application.port.IssuedToken;
import com.shaopc.worthit.auth.authentication.domain.AuthUser;
import com.shaopc.worthit.common.core.trace.TraceIdGenerator;
import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import com.shaopc.worthit.common.webmvc.error.DefaultErrorHttpStatusResolver;
import com.shaopc.worthit.common.webmvc.error.WorthItRestExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private static final String TRACE_ID = "trace-auth-001";

    private final AuthenticationService authenticationService =
            mock(AuthenticationService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TraceIdGenerator traceIdGenerator = () -> TRACE_ID;
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(authenticationService))
                .setControllerAdvice(new WorthItRestExceptionHandler(
                        new DefaultErrorHttpStatusResolver(),
                        traceIdGenerator))
                .build();
    }

    @Test
    void returnsWechatLoginContract() throws Exception {
        when(authenticationService.login(
                new com.shaopc.worthit.auth.authentication.application
                        .WechatLoginCommand("wx-code")))
                .thenReturn(new AuthenticationResult(
                        new IssuedToken("token-value", 2_592_000L),
                        new AuthUser(1938L, null, null, true),
                        true));

        mockMvc.perform(post("/api/v1/auth/wechat/login")
                        .requestAttr(SecurityHeaderNames.TRACE_ID, TRACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"wx-code"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").value("token-value"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(2_592_000))
                .andExpect(jsonPath("$.data.user.id").value("1938"))
                .andExpect(jsonPath("$.data.user.isNewUser").value(true))
                .andExpect(jsonPath("$.traceId").value(TRACE_ID));
    }

    @Test
    void rejectsBlankWechatCode() throws Exception {
        mockMvc.perform(post("/api/v1/auth/wechat/login")
                        .requestAttr(SecurityHeaderNames.TRACE_ID, TRACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":" "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VAL_INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.details[0].field").value("code"));
    }

    @Test
    void returnsCurrentUserContract() throws Exception {
        when(authenticationService.currentUser())
                .thenReturn(new AuthUser(1938L, "小值", null, true));

        mockMvc.perform(get("/api/v1/auth/me")
                        .requestAttr(SecurityHeaderNames.TRACE_ID, TRACE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("1938"))
                .andExpect(jsonPath("$.data.nickname").value("小值"))
                .andExpect(jsonPath("$.data.avatarUrl").doesNotExist())
                .andExpect(jsonPath("$.traceId").value(TRACE_ID));
    }

    @Test
    void logsOutCurrentSession() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .requestAttr(SecurityHeaderNames.TRACE_ID, TRACE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.traceId").value(TRACE_ID));

        verify(authenticationService).logout();
    }
}
