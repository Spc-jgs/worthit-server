package com.shaopc.worthit.auth.dataexport.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.web.error.CommonWebErrorCode;
import com.shaopc.worthit.reminder.client.response.ReminderDataExportResponse;
import com.shaopc.worthit.tracking.client.response.TrackingDataExportResponse;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** 使用稳定条目顺序和固定时间组装有界内存 ZIP。 */
@Component
public final class ZipDataExportArchiveAssembler
        implements DataExportArchiveAssembler {

    private static final int SCHEMA_VERSION = 1;
    private static final DateTimeFormatter FILE_NAME_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                    .withZone(ZoneOffset.UTC);
    private static final LocalDateTime ZIP_ENTRY_TIME =
            LocalDateTime.of(1980, 1, 1, 0, 0);

    private final ObjectMapper objectMapper;
    private final DataExportProperties properties;

    /** 创建归档组装器。 */
    public ZipDataExportArchiveAssembler(
            ObjectMapper objectMapper,
            DataExportProperties properties) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.properties = Objects.requireNonNull(properties);
    }

    @Override
    public DataExportArchive assemble(
            long userId,
            Instant exportedAt,
            AuthDataExportAccount account,
            TrackingDataExportResponse tracking,
            ReminderDataExportResponse reminder) {
        byte[] accountBytes = serializeFragment(account);
        byte[] trackingBytes = serializeFragment(tracking);
        byte[] reminderBytes = serializeFragment(reminder);
        List<ManifestFile> files = List.of(
                describe(
                        "auth/account.json", accountBytes, 1,
                        account.capturedAt()),
                describe(
                        "tracking/data.json", trackingBytes,
                        AuthDataExportServiceImpl.trackingRecordCount(tracking),
                        tracking.capturedAt()),
                describe(
                        "reminder/data.json", reminderBytes,
                        AuthDataExportServiceImpl.reminderRecordCount(reminder),
                        reminder.capturedAt()));
        byte[] manifestBytes = serialize(new Manifest(
                SCHEMA_VERSION,
                exportedAt,
                Long.toString(userId),
                files));

        try {
            BoundedOutputStream bounded =
                    new BoundedOutputStream(properties.maxArchiveBytes());
            try (ZipOutputStream zip = new ZipOutputStream(
                    bounded, StandardCharsets.UTF_8)) {
                writeEntry(zip, "manifest.json", manifestBytes);
                writeEntry(zip, "auth/account.json", accountBytes);
                writeEntry(zip, "tracking/data.json", trackingBytes);
                writeEntry(zip, "reminder/data.json", reminderBytes);
            }
            return new DataExportArchive(
                    "worthit-data-export-"
                            + FILE_NAME_TIME.format(exportedAt) + ".zip",
                    bounded.toByteArray());
        } catch (ArchiveLimitExceededException exception) {
            throw limitExceeded();
        } catch (IOException exception) {
            throw new BusinessException(
                    CommonWebErrorCode.SYS_ERROR,
                    "生成数据导出归档失败",
                    exception);
        }
    }

    private byte[] serializeFragment(Object value) {
        byte[] bytes = serialize(value);
        if (bytes.length > properties.maxFragmentBytes()) {
            throw limitExceeded();
        }
        return bytes;
    }

    private byte[] serialize(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(
                    CommonWebErrorCode.SYS_ERROR,
                    "序列化数据导出分片失败",
                    exception);
        }
    }

    private static ManifestFile describe(
            String path,
            byte[] body,
            long recordCount,
            Instant capturedAt) {
        return new ManifestFile(
                path,
                sha256(body),
                body.length,
                recordCount,
                capturedAt);
    }

    private static String sha256(byte[] body) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(body));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行时缺少SHA-256", exception);
        }
    }

    private static void writeEntry(
            ZipOutputStream zip,
            String path,
            byte[] body) throws IOException {
        ZipEntry entry = new ZipEntry(path);
        entry.setTimeLocal(ZIP_ENTRY_TIME);
        zip.putNextEntry(entry);
        zip.write(body);
        zip.closeEntry();
    }

    private static BusinessException limitExceeded() {
        return new BusinessException(
                CommonWebErrorCode.DATA_EXPORT_LIMIT_EXCEEDED);
    }

    private record Manifest(
            int schemaVersion,
            Instant exportedAt,
            String userId,
            List<ManifestFile> files) {
    }

    private record ManifestFile(
            String path,
            String sha256,
            long sizeBytes,
            long recordCount,
            Instant capturedAt) {
    }

    private static final class BoundedOutputStream extends OutputStream {

        private final int limit;
        private final ByteArrayOutputStream delegate =
                new ByteArrayOutputStream();

        private BoundedOutputStream(int limit) {
            this.limit = limit;
        }

        @Override
        public void write(int value) throws IOException {
            ensureCapacity(1);
            delegate.write(value);
        }

        @Override
        public void write(byte[] body, int offset, int length)
                throws IOException {
            ensureCapacity(length);
            delegate.write(body, offset, length);
        }

        private void ensureCapacity(int increment) throws IOException {
            if ((long) delegate.size() + increment > limit) {
                throw new ArchiveLimitExceededException();
            }
        }

        private byte[] toByteArray() {
            return delegate.toByteArray();
        }
    }

    private static final class ArchiveLimitExceededException
            extends IOException {
    }
}
