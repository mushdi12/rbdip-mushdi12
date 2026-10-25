package com.rbdip.bookstore.order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Применяет скидки в исходном порядке и округляет только итоговую сумму.
 */
@Component
public class PricingCalculator {

    private static final int BULK_QUANTITY_THRESHOLD = 10;
    private static final BigDecimal BULK_MULTIPLIER = new BigDecimal("0.95");
    private static final BigDecimal VIP_MULTIPLIER = new BigDecimal("0.9");
    private static final BigDecimal WHOLESALE_MULTIPLIER = new BigDecimal("0.85");
    private static final BigDecimal COUPON_MULTIPLIER = new BigDecimal("0.8");
    private static final BigDecimal LARGE_ORDER_THRESHOLD = new BigDecimal("1000");
    private static final BigDecimal LARGE_ORDER_MULTIPLIER = new BigDecimal("0.98");

    public record LineItem(BigDecimal price, int quantity) {
    }

    public BigDecimal calculateOrderTotal(List<LineItem> items, String customerType, String couponCode) {
        BigDecimal total = BigDecimal.ZERO;
        for (LineItem item : items) {
            total = total.add(calculateLineTotal(item));
        }
        total = applyCustomerDiscount(total, customerType);
        total = applyCoupon(total, couponCode).max(BigDecimal.ZERO);
        if (total.compareTo(LARGE_ORDER_THRESHOLD) > 0) {
            total = total.multiply(LARGE_ORDER_MULTIPLIER);
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateLineTotal(LineItem item) {
        BigDecimal lineTotal = item.price().multiply(BigDecimal.valueOf(item.quantity()));
        return item.quantity() > BULK_QUANTITY_THRESHOLD ? lineTotal.multiply(BULK_MULTIPLIER) : lineTotal;
    }

    private BigDecimal applyCustomerDiscount(BigDecimal total, String customerType) {
        if ("vip".equals(customerType)) {
            return total.multiply(VIP_MULTIPLIER);
        }
        if ("wholesale".equals(customerType)) {
            return total.multiply(WHOLESALE_MULTIPLIER);
        }
        return total;
    }

    private BigDecimal applyCoupon(BigDecimal total, String couponCode) {
        if ("SAVE10".equals(couponCode)) {
            return total.subtract(BigDecimal.TEN);
        }
        if ("SAVE20PERCENT".equals(couponCode)) {
            return total.multiply(COUPON_MULTIPLIER);
        }
        return total;
    }
}
