package com.shaopc.worthit.tracking;

import com.shaopc.worthit.tracking.category.domain.CategorySystemCode;
import com.shaopc.worthit.tracking.idempotency.application.TrackingOperation;
import com.shaopc.worthit.tracking.item.domain.ItemLifecycleStatus;
import com.shaopc.worthit.tracking.lifecycle.domain.DisposalType;
import com.shaopc.worthit.tracking.outbox.application.OutboxEventType;
import com.shaopc.worthit.tracking.outbox.application.OutboxStatus;
import com.shaopc.worthit.tracking.subscription.domain.AutoRenew;
import com.shaopc.worthit.tracking.subscription.domain.BillingCycleType;
import com.shaopc.worthit.tracking.subscription.domain.CurrencyCodes;
import com.shaopc.worthit.tracking.subscription.domain.SubscriptionStatus;
import com.shaopc.worthit.tracking.wish.domain.WishStatus;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrackingStableCodeTest {

    @Test
    void exposesFrozenCodesForDomainStates() {
        assertThat(ItemLifecycleStatus.HOLDING.code())
                .isEqualTo("HOLDING");
        assertThat(Arrays.stream(ItemLifecycleStatus.values())
                        .map(ItemLifecycleStatus::code))
                .containsExactly(
                        "HOLDING",
                        "RETURNED",
                        "SOLD",
                        "SCRAPPED");
        assertThat(Arrays.stream(DisposalType.values())
                        .map(DisposalType::code))
                .containsExactly(
                        "RETURNED",
                        "SOLD",
                        "SCRAPPED");
        assertThat(SubscriptionStatus.ACTIVE.code())
                .isEqualTo("ACTIVE");
        assertThat(SubscriptionStatus.PAUSED.code())
                .isEqualTo("PAUSED");
        assertThat(SubscriptionStatus.ENDED.code())
                .isEqualTo("ENDED");
        assertThat(WishStatus.CONSIDERING.code())
                .isEqualTo("CONSIDERING");
        assertThat(WishStatus.PURCHASED.code())
                .isEqualTo("PURCHASED");
        assertThat(WishStatus.ABANDONED.code())
                .isEqualTo("ABANDONED");
        assertThat(CategorySystemCode.UNCATEGORIZED.code())
                .isEqualTo("UNCATEGORIZED");
    }

    @Test
    void exposesFrozenCodesForExistingSubscriptionEnums() {
        assertThat(AutoRenew.YES.code()).isEqualTo("YES");
        assertThat(AutoRenew.NO.code()).isEqualTo("NO");
        assertThat(AutoRenew.UNKNOWN.code()).isEqualTo("UNKNOWN");
        assertThat(BillingCycleType.MONTHLY.code())
                .isEqualTo("MONTHLY");
        assertThat(BillingCycleType.YEARLY.code())
                .isEqualTo("YEARLY");
        assertThat(BillingCycleType.MULTI_MONTH.code())
                .isEqualTo("MULTI_MONTH");
        assertThat(BillingCycleType.FIXED_DAYS.code())
                .isEqualTo("FIXED_DAYS");
        assertThat(CurrencyCodes.CNY).isEqualTo("CNY");
    }

    @Test
    void exposesFrozenCodesForApplicationProtocols() {
        assertThat(Arrays.stream(TrackingOperation.values())
                        .map(TrackingOperation::code))
                .containsExactly(
                        "ITEM_CREATE",
                        "ITEM_UPDATE",
                        "ITEM_DELETE",
                        "ITEM_RESTORE",
                        "ITEM_RETURN",
                        "ITEM_SELL",
                        "ITEM_SCRAP",
                        "ITEM_REPLACE",
                        "SUB_CREATE",
                        "SUB_UPDATE",
                        "SUB_PAUSE",
                        "SUB_END",
                        "SUB_RESUME",
                        "SUB_DELETE",
                        "SUB_RESTORE",
                        "WISH_CREATE",
                        "WISH_UPDATE",
                        "WISH_PURCHASE",
                        "WISH_ABANDON",
                        "WISH_RECONSIDER",
                        "WISH_DELETE",
                        "WISH_RESTORE");
        assertThat(OutboxEventType.REMINDER_RECONCILE.code())
                .isEqualTo("REMINDER_RECONCILE");
        assertThat(OutboxStatus.NEW.code()).isEqualTo("NEW");
        assertThat(OutboxStatus.PROCESSING.code())
                .isEqualTo("PROCESSING");
        assertThat(OutboxStatus.RETRY_WAIT.code())
                .isEqualTo("RETRY_WAIT");
        assertThat(OutboxStatus.SUCCEEDED.code())
                .isEqualTo("SUCCEEDED");
        assertThat(OutboxStatus.DEAD.code()).isEqualTo("DEAD");
    }

    @Test
    void restoresEnumsFromPersistentCodes() {
        assertThat(ItemLifecycleStatus.fromCode("HOLDING"))
                .isSameAs(ItemLifecycleStatus.HOLDING);
        assertThat(DisposalType.fromCode("SOLD"))
                .isSameAs(DisposalType.SOLD);
        assertThat(SubscriptionStatus.fromCode("PAUSED"))
                .isSameAs(SubscriptionStatus.PAUSED);
        assertThat(WishStatus.fromCode("ABANDONED"))
                .isSameAs(WishStatus.ABANDONED);
        assertThat(CategorySystemCode.fromCode("UNCATEGORIZED"))
                .isSameAs(CategorySystemCode.UNCATEGORIZED);
        assertThat(AutoRenew.fromCode("UNKNOWN"))
                .isSameAs(AutoRenew.UNKNOWN);
        assertThat(BillingCycleType.fromCode("FIXED_DAYS"))
                .isSameAs(BillingCycleType.FIXED_DAYS);
        assertThat(OutboxEventType.fromCode("REMINDER_RECONCILE"))
                .isSameAs(OutboxEventType.REMINDER_RECONCILE);
    }

    @Test
    void rejectsUnknownPersistentCodes() {
        assertThatThrownBy(() ->
                SubscriptionStatus.fromCode("SUSPENDED"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SUSPENDED");
        assertThatThrownBy(() ->
                WishStatus.fromCode(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                OutboxEventType.fromCode("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UNKNOWN");
    }
}
