package com.rbdip.bookstore.order;

import com.rbdip.bookstore.purchase.PurchaseHistory;
import org.springframework.stereotype.Service;

/**
 * Адаптер истории заказов к публичному контракту проверки покупки.
 */
@Service
public class OrderPurchaseHistory implements PurchaseHistory {

    private final OrderItemRepository orderItemRepository;

    public OrderPurchaseHistory(OrderItemRepository orderItemRepository) {
        this.orderItemRepository = orderItemRepository;
    }

    @Override
    public boolean hasPurchased(Long productId, String customerName) {
        return orderItemRepository.existsPurchasedProduct(productId, customerName);
    }
}
