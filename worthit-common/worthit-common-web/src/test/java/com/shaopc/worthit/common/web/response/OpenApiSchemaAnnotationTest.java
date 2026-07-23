package com.shaopc.worthit.common.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiSchemaAnnotationTest {

    @Test
    void documentsApiResponseContract() throws NoSuchMethodException {
        Schema typeSchema = ApiResponse.class.getAnnotation(Schema.class);

        assertThat(typeSchema).isNotNull();
        assertThat(typeSchema.description()).isEqualTo("统一 API 响应信封");
        assertThat(schemaOf(ApiResponse.class, "traceId").description())
                .isEqualTo("可信调用链追踪标识");
        assertThat(schemaOf(ApiResponse.class, "details").description())
                .isEqualTo("字段校验详情");
    }

    @Test
    void documentsFieldViolationContract() throws NoSuchMethodException {
        Schema typeSchema = FieldViolation.class.getAnnotation(Schema.class);

        assertThat(typeSchema).isNotNull();
        assertThat(typeSchema.description()).isEqualTo("请求字段校验详情");
        assertThat(schemaOf(FieldViolation.class, "field").description())
                .isEqualTo("违反约束的字段名");
        assertThat(schemaOf(FieldViolation.class, "issue").description())
                .isEqualTo("中文问题描述");
    }

    private static Schema schemaOf(Class<?> type, String accessorName)
            throws NoSuchMethodException {
        Method accessor = type.getDeclaredMethod(accessorName);
        return accessor.getAnnotation(Schema.class);
    }
}
