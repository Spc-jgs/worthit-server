package com.shaopc.worthit.auth.dataexport.application;

import com.shaopc.worthit.common.core.error.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataExportAdmissionTest {

    @Test
    void rejectsSameUserAndFullInstanceThenReleasesIdempotently() {
        DataExportAdmission admission = new DataExportAdmission(
                new DataExportProperties(1, 1024, 2048));
        DataExportAdmission.Permit first = admission.acquire(1L);

        assertBusy(() -> admission.acquire(1L));
        assertBusy(() -> admission.acquire(2L));

        first.close();
        first.close();
        assertThatCode(() -> admission.acquire(2L).close())
                .doesNotThrowAnyException();
    }

    private static void assertBusy(Runnable operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.code())
                                .isEqualTo("DATA_EXPORT_BUSY"));
    }

    @Test
    void rejectsConfigurationAboveFrozenMaximums() {
        assertThatThrownBy(() -> new DataExportProperties(
                3, 8 * 1024 * 1024, 20 * 1024 * 1024))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new DataExportProperties(2, 1, 1).maxConcurrent())
                .isEqualTo(2);
    }
}
