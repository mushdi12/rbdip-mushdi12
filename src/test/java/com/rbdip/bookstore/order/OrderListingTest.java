package com.rbdip.bookstore.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.rbdip.bookstore.reference.AbstractIntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

/**
 * Проверяет содержимое списка и число SQL-запросов при росте числа заказов.
 */
class OrderListingTest extends AbstractIntegrationTest {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private OrderService orderService;

    @Test
    void createdOrderContainsItsSavedItems() {
        Long productId = createProduct("Book");
        Order order = orderService.createOrder(new CreateOrderRequest("Ivan", "Moscow", null, null, null,
                List.of(new CreateOrderRequest.Item(productId, 2))));

        assertThat(order.getItems()).hasSize(1);
        assertThat(order.getItems().getFirst().getProductName()).isEqualTo("Book");
        assertThat(order.getItems().getFirst().getQuantity()).isEqualTo(2);
        assertThat(order.getItems().getFirst().getOrderId()).isEqualTo(order.getId());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 12})
    void loadsAllOrderDetailsWithBoundedQueryCount(int orderCount) {
        Long first = createProduct("First");
        Long second = createProduct("Second");
        for (int i = 0; i < orderCount; i++) {
            var created = restTemplate.postForEntity("/orders",
                    new CreateOrderRequest("Customer " + i, "Moscow", null, null, null,
                            List.of(new CreateOrderRequest.Item(first, 1), new CreateOrderRequest.Item(second, 2))),
                    Map.class);
            assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        var listed = restTemplate.getForEntity("/orders", List.class);

        assertThat(listed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listed.getBody()).hasSize(orderCount);
        for (Object rawOrder : listed.getBody()) {
            Map<?, ?> order = (Map<?, ?>) rawOrder;
            assertThat(order.get("status")).isEqualTo("new");
            assertThat(order.get("customerFullName").toString()).startsWith("Customer ");
            assertThat((List<?>) order.get("items")).isEqualTo(List.of(
                    Map.of("productName", "First", "quantity", 1),
                    Map.of("productName", "Second", "quantity", 2)));
        }
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(3);
    }

    @Test
    void retainsOrdersWithoutItems() {
        Long productId = createProduct("Book");
        var created = restTemplate.postForEntity("/orders",
                new CreateOrderRequest("Ivan", "Moscow", null, null, null,
                        List.of(new CreateOrderRequest.Item(productId, 1))), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        jdbcTemplate.update("INSERT INTO orders (customer_id) SELECT customer_id FROM orders LIMIT 1");

        var listed = restTemplate.getForEntity("/orders", List.class);

        assertThat(listed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listed.getBody()).hasSize(2);
        assertThat(listed.getBody().stream().map(o -> ((List<?>) ((Map<?, ?>) o).get("items")).size()).toList())
                .containsExactlyInAnyOrder(0, 1);
    }

    private Long createProduct(String name) {
        var created = restTemplate.postForEntity("/products", Map.of("name", name, "price", 100), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return Long.valueOf(created.getBody().get("id").toString());
    }
}
