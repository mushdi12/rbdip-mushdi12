package com.rbdip.bookstore.order;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Координирует создание заказа. Все записи в БД выполняются в одной транзакции.
 */
@Service
public class OrderService {

    private static final String DEFAULT_CUSTOMER_TYPE = "regular";

    private final OrderValidator validator;
    private final OrderItemPreparer itemPreparer;
    private final PricingCalculator pricingCalculator;
    private final OrderPersistence persistence;
    private final OrderNotifier notifier;

    public OrderService(OrderValidator validator, OrderItemPreparer itemPreparer,
            PricingCalculator pricingCalculator, OrderPersistence persistence, OrderNotifier notifier) {
        this.validator = validator;
        this.itemPreparer = itemPreparer;
        this.pricingCalculator = pricingCalculator;
        this.persistence = persistence;
        this.notifier = notifier;
    }

    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        validator.validate(request);
        List<PreparedOrderItem> items = itemPreparer.prepare(request.items());
        String customerType = request.customerType() == null ? DEFAULT_CUSTOMER_TYPE : request.customerType();
        BigDecimal total = pricingCalculator.calculateOrderTotal(
                items.stream().map(PreparedOrderItem::pricingItem).toList(), customerType, request.couponCode());
        Order order = persistence.save(request, items);
        notifier.sendConfirmation(request.customerFullName(), order.getId(), total);
        return order;
    }
}
