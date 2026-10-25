package com.rbdip.bookstore.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rbdip.bookstore.reference.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

/**
 * Проверяет связи нормализованной схемы через API и ограничения PostgreSQL.
 */
class CustomerNormalizationTest extends AbstractIntegrationTest {

    @BeforeEach
    void cleanCustomers() {
        jdbcTemplate.execute("TRUNCATE customers RESTART IDENTITY CASCADE");
    }

    @Test
    void reusesCustomerWithIdenticalContactsAndKeepsProductReference() {
        Long productId = createProduct();
        createOrder(productId, "Moscow", null);
        createOrder(productId, "Moscow", null);

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM customers", Long.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM orders", Long.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForList("SELECT DISTINCT product_id FROM order_items", Long.class))
                .containsExactly(productId);
        jdbcTemplate.update("UPDATE products SET name = 'Renamed book' WHERE id = ?", productId);
        var response = restTemplate.getForEntity("/orders", List.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
        Map<?, ?> order = (Map<?, ?>) response.getBody().getFirst();
        assertThat(order.get("customerFullName")).isEqualTo("Ivan Petrov");
        Map<?, ?> item = (Map<?, ?>) ((List<?>) order.get("items")).getFirst();
        assertThat(item.get("productName")).isEqualTo("Renamed book");
        assertThat(item.get("quantity")).isEqualTo(1);
    }

    @Test
    void distinguishesCustomersWithDifferentContactDetails() {
        Long productId = createProduct();
        createOrder(productId, "Moscow", null);
        createOrder(productId, "SPb", null);
        createOrder(productId, "Moscow", "+7000");

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM customers", Long.class)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("SELECT count(DISTINCT customer_id) FROM orders", Long.class))
                .isEqualTo(3);
    }

    @Test
    void enforcesReferencesToExistingCustomersAndProducts() {
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO orders (customer_id) VALUES (-1)"))
                .isInstanceOf(DataIntegrityViolationException.class);
        Long productId = createProduct();
        createOrder(productId, "Moscow", null);
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE order_items SET product_id = -1"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Madonna", "Anna Maria Sidorova", " Ivan  Petrov ", "Ivan "})
    void preservesFullNameInApiAfterSplitting(String fullName) {
        Long productId = createProduct();
        var created = restTemplate.postForEntity("/orders",
                new CreateOrderRequest(fullName, "Moscow", null, null, null,
                        List.of(new CreateOrderRequest.Item(productId, 1))), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var listed = restTemplate.getForEntity("/orders", List.class);
        assertThat(listed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listed.getBody()).hasSize(1);
        Map<?, ?> order = (Map<?, ?>) listed.getBody().getFirst();
        assertThat(order.get("customerFullName")).isEqualTo(fullName);
    }

    private Long createProduct() {
        var response = restTemplate.postForEntity("/products", Map.of("name", "Book", "price", 100), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return Long.valueOf(response.getBody().get("id").toString());
    }

    private void createOrder(Long productId, String address, String phone) {
        var response = restTemplate.postForEntity("/orders",
                new CreateOrderRequest("Ivan Petrov", address, phone, null, null,
                        List.of(new CreateOrderRequest.Item(productId, 1))), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
