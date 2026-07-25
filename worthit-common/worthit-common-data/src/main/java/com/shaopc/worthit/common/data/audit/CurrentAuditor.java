package com.shaopc.worthit.common.data.audit;

import java.util.OptionalLong;

/**
 * 提供当前数据变更操作者标识。
 */
@FunctionalInterface
public interface CurrentAuditor {

    /**
     * 获取当前操作者。
     *
     * @return 当前用户标识；系统任务等无用户场景返回空
     */
    OptionalLong currentUserId();
}
