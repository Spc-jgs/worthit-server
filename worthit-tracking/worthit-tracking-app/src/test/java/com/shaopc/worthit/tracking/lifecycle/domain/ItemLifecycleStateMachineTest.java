package com.shaopc.worthit.tracking.lifecycle.domain;

import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.tracking.item.domain.Item;
import com.shaopc.worthit.tracking.item.domain.ItemLifecycleStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ItemLifecycleStateMachineTest {

    private static final LocalDate TODAY =
            LocalDate.of(2026, 7, 30);
    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 7, 30, 9, 30);

    @Test
    void returnsHoldingItemWithImmutablePurchaseSnapshot() {
        ItemDisposal disposal = decide(
                item(ItemLifecycleStatus.HOLDING),
                DisposalType.RETURNED,
                TODAY,
                null,
                "尺寸不合适");

        assertThat(disposal.type())
                .isEqualTo(DisposalType.RETURNED);
        assertThat(disposal.type().targetStatus())
                .isEqualTo(ItemLifecycleStatus.RETURNED);
        assertThat(disposal.purchasePriceSnapshot())
                .isEqualByComparingTo("1000.000000");
        assertThat(disposal.saleAmount()).isNull();
        assertThat(disposal.netCost()).isNull();
    }

    @Test
    void sellsHoldingItemAndCalculatesNetCostFromSnapshot() {
        ItemDisposal disposal = decide(
                item(ItemLifecycleStatus.HOLDING),
                DisposalType.SOLD,
                TODAY,
                new BigDecimal("800.000000"),
                null);

        assertThat(disposal.type().targetStatus())
                .isEqualTo(ItemLifecycleStatus.SOLD);
        assertThat(disposal.netCost())
                .isEqualByComparingTo("200.000000");
    }

    @Test
    void scrapsHoldingItemWithoutSaleAmount() {
        ItemDisposal disposal = decide(
                item(ItemLifecycleStatus.HOLDING),
                DisposalType.SCRAPPED,
                TODAY,
                null,
                null);

        assertThat(disposal.type().targetStatus())
                .isEqualTo(ItemLifecycleStatus.SCRAPPED);
        assertThat(disposal.saleAmount()).isNull();
    }

    @Test
    void rejectsAnyDisposalFromTerminalStatus() {
        for (ItemLifecycleStatus terminal :
                new ItemLifecycleStatus[]{
                    ItemLifecycleStatus.RETURNED,
                    ItemLifecycleStatus.SOLD,
                    ItemLifecycleStatus.SCRAPPED
                }) {
            assertThatThrownBy(() -> decide(
                    item(terminal),
                    DisposalType.SCRAPPED,
                    TODAY,
                    null,
                    null))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> assertThat(exception.code())
                                    .isEqualTo(
                                            "VAL_STATE_CONFLICT"));
        }
    }

    @Test
    void rejectsDisposalDateOutsidePurchaseAndToday() {
        assertInvalid(() -> decide(
                item(ItemLifecycleStatus.HOLDING),
                DisposalType.RETURNED,
                LocalDate.of(2026, 6, 30),
                null,
                null));
        assertInvalid(() -> decide(
                item(ItemLifecycleStatus.HOLDING),
                DisposalType.RETURNED,
                TODAY.plusDays(1),
                null,
                null));
    }

    @Test
    void rejectsSaleAmountOutsideDecimalContract() {
        assertInvalid(() -> decide(
                item(ItemLifecycleStatus.HOLDING),
                DisposalType.SOLD,
                TODAY,
                new BigDecimal("-0.000001"),
                null));
        assertInvalid(() -> decide(
                item(ItemLifecycleStatus.HOLDING),
                DisposalType.SOLD,
                TODAY,
                new BigDecimal("1.0000001"),
                null));
        assertInvalid(() -> decide(
                item(ItemLifecycleStatus.HOLDING),
                DisposalType.SOLD,
                TODAY,
                new BigDecimal("1000000000000.000000"),
                null));
    }

    @Test
    void enforcesSaleAmountByDisposalType() {
        assertInvalid(() -> decide(
                item(ItemLifecycleStatus.HOLDING),
                DisposalType.SOLD,
                TODAY,
                null,
                null));
        assertInvalid(() -> decide(
                item(ItemLifecycleStatus.HOLDING),
                DisposalType.RETURNED,
                TODAY,
                BigDecimal.ONE,
                null));
    }

    private static ItemDisposal decide(
            Item item,
            DisposalType type,
            LocalDate disposalDate,
            BigDecimal saleAmount,
            String remark) {
        return ItemLifecycleStateMachine.dispose(
                9001L,
                item,
                type,
                disposalDate,
                saleAmount,
                remark,
                TODAY,
                NOW);
    }

    private static Item item(ItemLifecycleStatus status) {
        return new Item(
                101L,
                1001L,
                10L,
                "测试物品",
                new BigDecimal("1000.000000"),
                new BigDecimal("3.000"),
                null,
                LocalDate.of(2026, 7, 1),
                null,
                false,
                null,
                null,
                status,
                1L,
                NOW.minusDays(30),
                NOW.minusDays(30));
    }

    private static void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo(
                                        "VAL_INVALID_ARGUMENT"));
    }
}
