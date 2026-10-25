package com.rbdip.bookstore.order;

import com.rbdip.bookstore.customer.Customer;
import com.rbdip.bookstore.customer.CustomerRegistry;
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
    private final CustomerRegistry customerRegistry;

    public OrderPersistence(OrderRepository orderRepository, OrderItemRepository orderItemRepository,
            CustomerRegistry customerRegistry) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.customerRegistry = customerRegistry;
    }

    public Order save(CreateOrderRequest request, List<PreparedOrderItem> items) {
        Customer customer = customerRegistry.findOrCreate(
                request.customerFullName(), request.customerAddress(), request.customerPhone());
        Order order = orderRepository.save(new Order(customer, INITIAL_STATUS));
        for (PreparedOrderItem item : items) {
            orderItemRepository.save(new OrderItem(order.getId(), item.product(), item.quantity()));
        }
        return order;
    }
}
