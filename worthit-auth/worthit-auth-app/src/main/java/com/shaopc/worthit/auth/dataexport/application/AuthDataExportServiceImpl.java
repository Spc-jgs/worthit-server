package com.shaopc.worthit.auth.dataexport.application;

import com.shaopc.worthit.auth.authentication.application.port.UserSession;
import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.http.error.RemoteServiceException;
import com.shaopc.worthit.common.web.error.CommonWebErrorCode;
import com.shaopc.worthit.reminder.client.api.ReminderDataExportClient;
import com.shaopc.worthit.reminder.client.response.ReminderDataExportResponse;
import com.shaopc.worthit.tracking.client.api.TrackingDataExportClient;
import com.shaopc.worthit.tracking.client.response.TrackingDataExportResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** Auth 数据导出的同步、全有或全无实现。 */
@Service
public final class AuthDataExportServiceImpl implements AuthDataExportService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(AuthDataExportServiceImpl.class);
    private static final String CAPACITY_CODE =
            CommonWebErrorCode.DATA_EXPORT_LIMIT_EXCEEDED.code();
    private static final long MAX_REMOTE_RECORDS = 10_000L;
    private static final String TIME_ZONE = "Asia/Shanghai";

    private final UserSession userSession;
    private final AuthDataExportQuery accountQuery;
    private final TrackingDataExportClient trackingClient;
    private final ReminderDataExportClient reminderClient;
    private final DataExportAdmission admission;
    private final DataExportArchiveAssembler archiveAssembler;
    private final Clock clock;

    /** 创建数据导出用例。 */
    public AuthDataExportServiceImpl(
            UserSession userSession,
            AuthDataExportQuery accountQuery,
            TrackingDataExportClient trackingClient,
            ReminderDataExportClient reminderClient,
            DataExportAdmission admission,
            DataExportArchiveAssembler archiveAssembler,
            Clock clock) {
        this.userSession = Objects.requireNonNull(userSession);
        this.accountQuery = Objects.requireNonNull(accountQuery);
        this.trackingClient = Objects.requireNonNull(trackingClient);
        this.reminderClient = Objects.requireNonNull(reminderClient);
        this.admission = Objects.requireNonNull(admission);
        this.archiveAssembler = Objects.requireNonNull(archiveAssembler);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public DataExportArchive exportCurrentUserData() {
        long userId = userSession.currentUserId();
        try (DataExportAdmission.Permit ignored = admission.acquire(userId)) {
            Instant exportedAt = clock.instant();
            AuthDataExportAccount account = accountQuery.exportAccount(userId);
            validateAccount(userId, account);
            TrackingDataExportResponse tracking = requireRemoteFragment(
                    "Tracking", fetchTracking(userId));
            validateRemoteFragment(
                    "Tracking",
                    userId,
                    tracking.schemaVersion(),
                    tracking.userId(),
                    tracking.capturedAt(),
                    tracking.timeZone());
            ReminderDataExportResponse reminder = requireRemoteFragment(
                    "Reminder", fetchReminder(userId));
            validateRemoteFragment(
                    "Reminder",
                    userId,
                    reminder.schemaVersion(),
                    reminder.userId(),
                    reminder.capturedAt(),
                    reminder.timeZone());
            enforceRemoteRecordLimits(tracking, reminder);
            DataExportArchive archive = archiveAssembler.assemble(
                    userId, exportedAt, account, tracking, reminder);
            LOGGER.info(
                    "数据导出完成 trackingRecords={}, "
                            + "reminderRecords={}, archiveBytes={}",
                    trackingRecordCount(tracking),
                    reminderRecordCount(reminder),
                    archive.content().length);
            return archive;
        }
    }

    private TrackingDataExportResponse fetchTracking(long userId) {
        try {
            return trackingClient.exportUserData(userId);
        } catch (RemoteServiceException exception) {
            throw mapRemoteCapacity(exception);
        } catch (RestClientException exception) {
            throw upstreamFailure(exception);
        }
    }

    private ReminderDataExportResponse fetchReminder(long userId) {
        try {
            return reminderClient.exportUserData(userId);
        } catch (RemoteServiceException exception) {
            throw mapRemoteCapacity(exception);
        } catch (RestClientException exception) {
            throw upstreamFailure(exception);
        }
    }

    private static RuntimeException mapRemoteCapacity(
            RemoteServiceException exception) {
        if (CAPACITY_CODE.equals(exception.remoteCode())) {
            return new BusinessException(
                    CommonWebErrorCode.DATA_EXPORT_LIMIT_EXCEEDED);
        }
        return exception;
    }

    private static BusinessException upstreamFailure(
            RestClientException exception) {
        return new BusinessException(
                CommonWebErrorCode.SYS_UPSTREAM,
                CommonWebErrorCode.SYS_UPSTREAM.defaultMessage(),
                exception);
    }

    private static void validateAccount(
            long expectedUserId,
            AuthDataExportAccount account) {
        if (account == null
                || account.account() == null
                || account.schemaVersion() != 1
                || account.capturedAt() == null
                || !TIME_ZONE.equals(account.timeZone())
                || !Long.toString(expectedUserId).equals(account.userId())
                || !Long.toString(expectedUserId).equals(account.account().id())) {
            throw new BusinessException(
                    CommonWebErrorCode.SYS_ERROR,
                    "Auth导出分片契约不一致");
        }
    }

    private static void validateRemoteFragment(
            String service,
            long expectedUserId,
            int schemaVersion,
            String actualUserId,
            Instant capturedAt,
            String timeZone) {
        if (schemaVersion != 1
                || capturedAt == null
                || !TIME_ZONE.equals(timeZone)
                || !Long.toString(expectedUserId).equals(actualUserId)) {
            throw new BusinessException(
                    CommonWebErrorCode.SYS_UPSTREAM,
                    service + "导出分片契约不一致");
        }
    }

    private static <T> T requireRemoteFragment(String service, T fragment) {
        if (fragment == null) {
            throw new BusinessException(
                    CommonWebErrorCode.SYS_UPSTREAM,
                    service + "导出分片为空");
        }
        return fragment;
    }

    private static void enforceRemoteRecordLimits(
            TrackingDataExportResponse tracking,
            ReminderDataExportResponse reminder) {
        if (trackingRecordCount(tracking) > MAX_REMOTE_RECORDS
                || reminderRecordCount(reminder) > MAX_REMOTE_RECORDS) {
            throw new BusinessException(
                    CommonWebErrorCode.DATA_EXPORT_LIMIT_EXCEEDED);
        }
    }

    static long trackingRecordCount(TrackingDataExportResponse response) {
        return (long) response.categories().size()
                + response.items().size()
                + response.subscriptions().size()
                + response.wishes().size()
                + response.disposals().size()
                + response.replacements().size();
    }

    static long reminderRecordCount(ReminderDataExportResponse response) {
        return (long) response.bindings().size() + response.instances().size();
    }
}
