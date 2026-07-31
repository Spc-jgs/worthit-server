package com.shaopc.worthit.tracking.lifecycle.infrastructure.persistence;

import com.shaopc.worthit.common.core.pagination.PageQuery;
import com.shaopc.worthit.common.core.pagination.PageResult;
import com.shaopc.worthit.tracking.lifecycle.application.LifecycleDisposalReview;
import com.shaopc.worthit.tracking.lifecycle.application.LifecycleItemBrief;
import com.shaopc.worthit.tracking.lifecycle.application.LifecycleReplacementReview;
import com.shaopc.worthit.tracking.lifecycle.application.LifecycleReviewEntry;
import com.shaopc.worthit.tracking.lifecycle.application.LifecycleReviewEntryType;
import com.shaopc.worthit.tracking.lifecycle.application.LifecycleReviewQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/**
 * MySQL 联合查询实现的生命周期复盘读模型。
 */
@Repository
@RequiredArgsConstructor
public class MybatisLifecycleReviewQuery
        implements LifecycleReviewQuery {

    private final LifecycleReviewMapper mapper;

    @Override
    public PageResult<LifecycleReviewEntry> findPage(
            long userId, PageQuery pageQuery) {
        long offset = (long) (pageQuery.page() - 1)
                * pageQuery.size();
        List<LifecycleReviewEntry> items =
                mapper.selectPage(
                                userId,
                                offset,
                                pageQuery.size())
                        .stream()
                        .map(MybatisLifecycleReviewQuery::toEntry)
                        .toList();
        return PageResult.of(
                items,
                pageQuery,
                mapper.countAll(userId));
    }

    private static LifecycleReviewEntry toEntry(
            LifecycleReviewRow row) {
        LifecycleReviewEntryType type =
                LifecycleReviewEntryType.valueOf(
                        row.getEntryType());
        if (type == LifecycleReviewEntryType.DISPOSAL) {
            return new LifecycleReviewEntry(
                    row.getId(),
                    type,
                    row.getEventDate(),
                    row.getCreateTime(),
                    new LifecycleDisposalReview(
                            new LifecycleItemBrief(
                                    row.getItemId(),
                                    row.getItemName()),
                            row.getDisposalType(),
                            row.getDisposalDate(),
                            decimal(row.getSaleAmount()),
                            netCost(row)),
                    null);
        }
        return new LifecycleReviewEntry(
                row.getId(),
                type,
                row.getEventDate(),
                row.getCreateTime(),
                null,
                new LifecycleReplacementReview(
                        new LifecycleItemBrief(
                                row.getOldItemId(),
                                row.getOldItemName()),
                        new LifecycleItemBrief(
                                row.getNewItemId(),
                                row.getNewItemName())));
    }

    private static String netCost(LifecycleReviewRow row) {
        if (!"SOLD".equals(row.getDisposalType())) {
            return null;
        }
        return decimal(
                row.getPurchasePriceSnapshot()
                        .subtract(row.getSaleAmount()));
    }

    private static String decimal(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}
