package com.rbdip.bookstore.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PricingRegressionTest {

    private final PricingCalculator calculator = new PricingCalculator();

    @ParameterizedTest
    @CsvSource({
        "100, 10, regular, NONE, 1000.00",
        "100, 11, regular, NONE, 1024.10",
        "100, 2, vip, SAVE10, 170.00",
        "100, 2, wholesale, SAVE20PERCENT, 136.00",
        "5, 1, regular, SAVE10, 0.00",
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
}
