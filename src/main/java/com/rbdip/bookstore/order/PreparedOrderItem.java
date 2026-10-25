package com.rbdip.bookstore.order;

import com.rbdip.bookstore.product.Product;

/**
 * Хранит товар вместе с проверенным количеством вместо двух параллельных списков.
 */
public record PreparedOrderItem(Product product, int quantity) {

    public PricingCalculator.LineItem pricingItem() {
        return new PricingCalculator.LineItem(product.getPrice(), quantity);
    }
}
