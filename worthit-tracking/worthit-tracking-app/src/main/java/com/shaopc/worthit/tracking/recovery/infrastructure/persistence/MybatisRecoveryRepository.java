package com.shaopc.worthit.tracking.recovery.infrastructure.persistence;

import com.shaopc.worthit.common.core.pagination.PageQuery;
import com.shaopc.worthit.common.core.pagination.PageResult;
import com.shaopc.worthit.tracking.recovery.domain.DeletedResource;
import com.shaopc.worthit.tracking.recovery.domain.RecoveryRepository;
import com.shaopc.worthit.tracking.recovery.domain.RecoveryResourceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 基于 MyBatis 的完整恢复只读投影仓储。
 */
@Repository
@RequiredArgsConstructor
public class MybatisRecoveryRepository
        implements RecoveryRepository {

    private final RecoveryMapper mapper;

    @Override
    public PageResult<DeletedResource> findDeletedPage(
            long userId,
            RecoveryResourceType resourceType,
            PageQuery pageQuery) {
        String type = resourceType == null
                ? null : resourceType.name();
        long offset = (long) (pageQuery.page() - 1)
                * pageQuery.size();
        List<DeletedResource> items = mapper.selectDeletedPage(
                        userId,
                        type,
                        offset,
                        pageQuery.size())
                .stream()
                .map(MybatisRecoveryRepository::toDomain)
                .toList();
        return PageResult.of(
                items,
                pageQuery,
                mapper.countDeleted(userId, type));
    }

    private static DeletedResource toDomain(
            RecoveryResourceDO data) {
        return new DeletedResource(
                data.getId(),
                RecoveryResourceType.valueOf(
                        data.getResourceType()),
                data.getName(),
                data.getCategoryId(),
                data.getCategoryName(),
                Boolean.TRUE.equals(
                        data.getCategoryAvailable()),
                data.getStatus(),
                data.getVersion(),
                data.getDeletedAt());
    }
}
