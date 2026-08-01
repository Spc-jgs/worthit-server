package com.shaopc.worthit.tracking.category.interfaces.rest;

import com.shaopc.worthit.common.core.trace.TraceIdGenerator;
import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import com.shaopc.worthit.common.webmvc.error.DefaultErrorHttpStatusResolver;
import com.shaopc.worthit.common.webmvc.error.WorthItRestExceptionHandler;
import com.shaopc.worthit.tracking.category.application.CategoryService;
import com.shaopc.worthit.tracking.category.domain.Category;
import com.shaopc.worthit.tracking.category.domain.CategorySystemCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CategoryControllerTest {

    private static final String TRACE_ID = "trace-category-001";

    private final CategoryService categoryService =
            mock(CategoryService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TraceIdGenerator traceIdGenerator = () -> TRACE_ID;
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CategoryController(categoryService))
                .setControllerAdvice(new WorthItRestExceptionHandler(
                        new DefaultErrorHttpStatusResolver(),
                        traceIdGenerator))
                .build();
    }

    @Test
    void returnsCategoryListContract() throws Exception {
        when(categoryService.list()).thenReturn(List.of(
                new Category(
                        1938L, 1001L, "未分类",
                        CategorySystemCode.UNCATEGORIZED),
                new Category(1939L, 1001L, "数码", null)));

        mockMvc.perform(get("/api/v1/categories")
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID, TRACE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("1938"))
                .andExpect(jsonPath("$.data[0].name").value("未分类"))
                .andExpect(jsonPath("$.data[0].systemCode")
                        .value("UNCATEGORIZED"))
                .andExpect(jsonPath("$.data[0].deletable").value(false))
                .andExpect(jsonPath("$.data[1].deletable").value(true))
                .andExpect(jsonPath("$.traceId").value(TRACE_ID));
    }

    @Test
    void createsCategoryAndReturnsContract() throws Exception {
        when(categoryService.create("数码")).thenReturn(
                new Category(1939L, 1001L, "数码", null));

        mockMvc.perform(post("/api/v1/categories")
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID, TRACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"数码"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("1939"))
                .andExpect(jsonPath("$.data.name").value("数码"))
                .andExpect(jsonPath("$.data.systemCode").doesNotExist())
                .andExpect(jsonPath("$.data.deletable").value(true));
    }

    @Test
    void rejectsBlankCategoryName() throws Exception {
        mockMvc.perform(post("/api/v1/categories")
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID, TRACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":" "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VAL_INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.details[0].field")
                        .value("name"));
    }

    @Test
    void rejectsCategoryNameLongerThanDatabaseContract() throws Exception {
        String tooLongName = "分".repeat(33);

        mockMvc.perform(post("/api/v1/categories")
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID, TRACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s"}
                                """.formatted(tooLongName)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VAL_INVALID_ARGUMENT"));
    }

    @Test
    void renamesCategoryAndReturnsContract() throws Exception {
        when(categoryService.rename(1939L, "办公设备")).thenReturn(
                new Category(1939L, 1001L, "办公设备", null));

        mockMvc.perform(patch("/api/v1/categories/1939")
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID, TRACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"办公设备"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("1939"))
                .andExpect(jsonPath("$.data.name").value("办公设备"))
                .andExpect(jsonPath("$.data.deletable").value(true))
                .andExpect(jsonPath("$.traceId").value(TRACE_ID));

        verify(categoryService).rename(1939L, "办公设备");
    }

    @Test
    void rejectsBlankRename() throws Exception {
        mockMvc.perform(patch("/api/v1/categories/1939")
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID, TRACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":" "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VAL_INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.details[0].field")
                        .value("name"));
    }

    @Test
    void deletesCategory() throws Exception {
        mockMvc.perform(delete("/api/v1/categories/1939")
                        .requestAttr(
                                SecurityHeaderNames.TRACE_ID, TRACE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.traceId").value(TRACE_ID));

        verify(categoryService).delete(1939L);
    }
}
