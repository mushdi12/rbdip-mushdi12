package com.rbdip.bookstore.order;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {

    private final OrderService orderService;
    private final OrderRepository orderRepository;

    public OrderController(OrderService orderService, OrderRepository orderRepository) {
        this.orderService = orderService;
        this.orderRepository = orderRepository;
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createOrder(@RequestBody CreateOrderRequest request) {
        Order order = orderService.createOrder(request);
        return Map.of("id", order.getId(), "status", order.getStatus());
    }

    @GetMapping("/orders")
    public List<Map<String, Object>> listOrders() {
        return orderRepository.findAllWithDetails().stream()
                .map(order -> Map.<String, Object>of(
                        "id", order.getId(),
                        "customerFullName", order.getCustomerFullName(),
                        "status", order.getStatus(),
                        "items", order.getItems().stream()
                                .map(item -> Map.of("productName", item.getProductName(), "quantity", item.getQuantity()))
                                .toList()))
                .toList();
    }
}
