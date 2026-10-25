package com.rbdip.bookstore.order;

import com.rbdip.bookstore.product.Product;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Сохраняет заказ и его позиции в транзакции вызывающего сервиса.
 */
@Component
public class OrderPersistence {

    private static final String INITIAL_STATUS = "new";

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    public OrderPersistence(OrderRepository orderRepository, OrderItemRepository orderItemRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
    }

    public Order save(CreateOrderRequest request, List<PreparedOrderItem> items) {
        Order order = orderRepository.save(new Order(
                request.customerFullName(), request.customerAddress(), request.customerPhone(), INITIAL_STATUS));
        for (PreparedOrderItem item : items) {
            Product product = item.product();
            orderItemRepository.save(new OrderItem(
                    order.getId(), product.getName(), product.getPrice(), item.quantity()));
        }
        return order;
    }
}
