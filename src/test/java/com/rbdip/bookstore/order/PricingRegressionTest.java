package com.rbdip.bookstore.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PricingRegressionTest {

    private final PricingCalculator calculator = new PricingCalculator();

    @ParameterizedTest
    @CsvSource({
        "100, 9, regular, NONE, 900.00",
        "100, 10, regular, NONE, 1000.00",
        "100, 11, regular, NONE, 1024.10",
        "100, 2, vip, NONE, 180.00",
        "100, 2, wholesale, NONE, 170.00",
        "100, 2, regular, SAVE10, 190.00",
        "100, 2, regular, SAVE20PERCENT, 160.00",
        "100, 2, vip, SAVE10, 170.00",
        "100, 2, wholesale, SAVE20PERCENT, 136.00",
        "100, 2, vip, SAVE20PERCENT, 144.00",
        "100, 2, wholesale, SAVE10, 160.00",
        "5, 1, regular, SAVE10, 0.00",
        "10, 1, regular, SAVE10, 0.00",
        "10.01, 1, regular, SAVE10, 0.01",
        "999.99, 1, regular, NONE, 999.99",
        "1000, 1, regular, NONE, 1000.00",
        "1000.01, 1, regular, NONE, 980.01",
        "1010, 1, regular, SAVE10, 1000.00",
        "1100, 1, vip, NONE, 990.00",
        "1250, 1, regular, SAVE20PERCENT, 1000.00",
        "100, 11, vip, SAVE20PERCENT, 752.40",
        "100, 2, unknown, UNKNOWN, 200.00",
        "100, 2, VIP, save10, 200.00",
        "100, 2, , , 200.00",
        "0, 1, regular, NONE, 0.00",
        "100, 0, regular, NONE, 0.00",
        "100, -1, regular, NONE, 0.00",
        "-100, 1, regular, NONE, 0.00",
        "10.004, 1, regular, NONE, 10.00",
        "10.005, 1, regular, NONE, 10.01"
    })
    void preservesDiscountOrderAndRounding(
            String price, int quantity, String customerType, String coupon, String expected) {
        BigDecimal result = calculator.calculateOrderTotal(
                List.of(new PricingCalculator.LineItem(new BigDecimal(price), quantity)),
                customerType, coupon);

        assertThat(result).isEqualByComparingTo(expected);
        assertThat(result.scale()).isEqualTo(2);
    }

    @Test
    void appliesBulkDiscountToEachLineSeparately() {
        BigDecimal result = calculator.calculateOrderTotal(List.of(
                new PricingCalculator.LineItem(new BigDecimal("10"), 11),
                new PricingCalculator.LineItem(new BigDecimal("10"), 10)), "regular", null);

        assertThat(result).isEqualByComparingTo("204.50");
    }

    @Test
    void roundsOnlyAfterAddingAllLines() {
        BigDecimal result = calculator.calculateOrderTotal(List.of(
                new PricingCalculator.LineItem(new BigDecimal("0.004"), 1),
                new PricingCalculator.LineItem(new BigDecimal("0.004"), 1)), "regular", null);

        assertThat(result).isEqualByComparingTo("0.01");
    }

    @Test
    void returnsZeroForEmptyOrderEvenWithCoupon() {
        assertThat(calculator.calculateOrderTotal(List.of(), null, "SAVE10")).isEqualByComparingTo("0.00");
    }

    @Test
    void rejectsMissingItemList() {
        assertThatNullPointerException().isThrownBy(() -> calculator.calculateOrderTotal(null, null, null));
    }

    @Test
    void rejectsMissingPrice() {
        assertThatNullPointerException().isThrownBy(() -> calculator.calculateOrderTotal(
                List.of(new PricingCalculator.LineItem(null, 1)), null, null));
    }
}
