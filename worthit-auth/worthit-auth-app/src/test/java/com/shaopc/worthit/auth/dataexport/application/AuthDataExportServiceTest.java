package com.shaopc.worthit.auth.dataexport.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shaopc.worthit.auth.authentication.application.port.UserSession;
import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.http.error.RemoteServiceException;
import com.shaopc.worthit.reminder.client.api.ReminderDataExportClient;
import com.shaopc.worthit.reminder.client.response.ReminderDataExportResponse;
import com.shaopc.worthit.tracking.client.api.TrackingDataExportClient;
import com.shaopc.worthit.tracking.client.response.TrackingDataExportResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthDataExportServiceTest {

    private static final Instant EXPORTED_AT =
            Instant.parse("2026-08-04T10:11:12Z");
    private static final Instant CAPTURED_AT =
            Instant.parse("2026-08-04T10:11:11Z");

    @Test
    void createsStableCompleteArchiveForCurrentUser() throws Exception {
        Fixture fixture = new Fixture();

        DataExportArchive archive = fixture.service.exportCurrentUserData();

        assertThat(archive.fileName())
                .isEqualTo("worthit-data-export-20260804T101112Z.zip");
        List<ArchiveEntry> entries = readEntries(archive.content());
        assertThat(entries).extracting(ArchiveEntry::name).containsExactly(
                "manifest.json",
                "auth/account.json",
                "tracking/data.json",
                "reminder/data.json");
        JsonNode manifest = fixture.objectMapper.readTree(entries.get(0).body());
        assertThat(manifest.path("userId").asText()).isEqualTo("42");
        assertThat(manifest.path("files")).hasSize(3);
        assertThat(manifest.path("files").get(1).path("recordCount").asInt())
                .isEqualTo(1);
        for (int index = 0; index < 3; index++) {
            JsonNode description = manifest.path("files").get(index);
            byte[] body = entries.get(index + 1).body();
            assertThat(description.path("path").asText())
                    .isEqualTo(entries.get(index + 1).name());
            assertThat(description.path("sizeBytes").asLong())
                    .isEqualTo(body.length);
            assertThat(description.path("sha256").asText())
                    .isEqualTo(java.util.HexFormat.of().formatHex(
                            MessageDigest.getInstance("SHA-256").digest(body)));
        }
        assertThat(new String(entries.get(1).body(), StandardCharsets.UTF_8))
                .doesNotContain(
                "password", "openid", "unionid", "token", "secret");
        assertThat(fixture.service.exportCurrentUserData().content())
                .containsExactly(archive.content());
    }

    @Test
    void mapsRemoteCapacityFailureAndAlwaysReleasesAdmission() {
        Fixture fixture = new Fixture();
        when(fixture.trackingClient.exportUserData(42L))
                .thenThrow(new RemoteServiceException(
                        "worthit-tracking",
                        413,
                        "DATA_EXPORT_LIMIT_EXCEEDED",
                        "remote-trace",
                        "remote detail"))
                .thenReturn(trackingResponse());

        assertThatThrownBy(fixture.service::exportCurrentUserData)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.code())
                                .isEqualTo("DATA_EXPORT_LIMIT_EXCEEDED"));
        assertThat(fixture.service.exportCurrentUserData().content()).isNotEmpty();
    }

    @Test
    void rejectsMismatchedFragmentIdentityAsUpstreamFailure() {
        Fixture fixture = new Fixture();
        when(fixture.trackingClient.exportUserData(42L)).thenReturn(
                new TrackingDataExportResponse(
                        1, CAPTURED_AT, "Asia/Shanghai", "43",
                        List.of(), List.of(), List.of(), List.of(),
                        List.of(), List.of()));

        assertThatThrownBy(fixture.service::exportCurrentUserData)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.code()).isEqualTo("SYS_UPSTREAM"));
    }

    @Test
    void mapsTransportFailureToSafeUpstreamError() {
        Fixture fixture = new Fixture();
        when(fixture.trackingClient.exportUserData(42L)).thenThrow(
                new ResourceAccessException(
                        "connection detail", new IOException("socket detail")));

        assertThatThrownBy(fixture.service::exportCurrentUserData)
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.code()).isEqualTo("SYS_UPSTREAM");
                    assertThat(exception.getMessage())
                            .isEqualTo("下游服务暂时不可用");
                });
    }

    @Test
    void enforcesFragmentAndFinalArchiveByteLimits() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        AuthDataExportAccount account = new AuthDataExportAccount(
                1, CAPTURED_AT, "Asia/Shanghai", "42",
                new AuthDataExportAccount.Account(
                        "42", "tester", null, "ACTIVE",
                        CAPTURED_AT, CAPTURED_AT));

        assertLimit(() -> new ZipDataExportArchiveAssembler(
                mapper, new DataExportProperties(2, 1, 20 * 1024 * 1024))
                .assemble(
                        42L, EXPORTED_AT, account,
                        trackingResponse(), reminderResponse()));
        assertLimit(() -> new ZipDataExportArchiveAssembler(
                mapper, new DataExportProperties(2, 8 * 1024 * 1024, 1))
                .assemble(
                        42L, EXPORTED_AT, account,
                        trackingResponse(), reminderResponse()));
    }

    private static void assertLimit(Runnable operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.code())
                                .isEqualTo("DATA_EXPORT_LIMIT_EXCEEDED"));
    }

    private static List<ArchiveEntry> readEntries(byte[] archive) throws Exception {
        List<ArchiveEntry> entries = new ArrayList<>();
        try (ZipInputStream input = new ZipInputStream(
                new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                entries.add(new ArchiveEntry(entry.getName(), input.readAllBytes()));
            }
        }
        return entries;
    }

    private static TrackingDataExportResponse trackingResponse() {
        return new TrackingDataExportResponse(
                1,
                CAPTURED_AT,
                "Asia/Shanghai",
                "42",
                List.of(new TrackingDataExportResponse.Category(
                        "100", "电子产品", "ELECTRONICS", 1L,
                        CAPTURED_AT, CAPTURED_AT, false, null)),
                List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static ReminderDataExportResponse reminderResponse() {
        return new ReminderDataExportResponse(
                1, CAPTURED_AT, "Asia/Shanghai", "42",
                List.of(), List.of());
    }

    private record ArchiveEntry(String name, byte[] body) {
    }

    private static final class Fixture {

        private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        private final TrackingDataExportClient trackingClient =
                mock(TrackingDataExportClient.class);
        private final ReminderDataExportClient reminderClient =
                mock(ReminderDataExportClient.class);
        private final AuthDataExportService service;

        private Fixture() {
            UserSession userSession = mock(UserSession.class);
            when(userSession.currentUserId()).thenReturn(42L);
            AuthDataExportQuery query = userId -> new AuthDataExportAccount(
                    1,
                    CAPTURED_AT,
                    "Asia/Shanghai",
                    Long.toString(userId),
                    new AuthDataExportAccount.Account(
                            Long.toString(userId),
                            "tester",
                            "7",
                            "ACTIVE",
                            CAPTURED_AT,
                            CAPTURED_AT));
            when(trackingClient.exportUserData(42L)).thenReturn(trackingResponse());
            when(reminderClient.exportUserData(42L)).thenReturn(reminderResponse());
            DataExportProperties properties = new DataExportProperties(
                    2, 8 * 1024 * 1024, 20 * 1024 * 1024);
            service = new AuthDataExportServiceImpl(
                    userSession,
                    query,
                    trackingClient,
                    reminderClient,
                    new DataExportAdmission(properties),
                    new ZipDataExportArchiveAssembler(objectMapper, properties),
                    Clock.fixed(EXPORTED_AT, ZoneOffset.UTC));
        }
    }
}
