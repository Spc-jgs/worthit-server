package com.shaopc.worthit.auth.dataexport.application;

import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.web.error.CommonWebErrorCode;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/** 单实例导出并发门禁，同一用户同时只允许一个导出。 */
@Component
public final class DataExportAdmission {

    private final Semaphore permits;
    private final Set<Long> activeUsers = ConcurrentHashMap.newKeySet();

    /** 使用冻结的实例并发上限创建门禁。 */
    public DataExportAdmission(DataExportProperties properties) {
        permits = new Semaphore(properties.maxConcurrent());
    }

    /**
     * 非阻塞申请当前用户的导出资格。
     *
     * @return 必须在 finally 或 try-with-resources 中关闭的许可
     */
    public Permit acquire(long userId) {
        if (!activeUsers.add(userId)) {
            throw busy();
        }
        if (!permits.tryAcquire()) {
            activeUsers.remove(userId);
            throw busy();
        }
        return new Permit(userId);
    }

    private static BusinessException busy() {
        return new BusinessException(CommonWebErrorCode.DATA_EXPORT_BUSY);
    }

    /** 幂等释放实例槽和用户占用。 */
    public final class Permit implements AutoCloseable {

        private final long userId;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Permit(long userId) {
            this.userId = userId;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                activeUsers.remove(userId);
                permits.release();
            }
        }
    }
}
