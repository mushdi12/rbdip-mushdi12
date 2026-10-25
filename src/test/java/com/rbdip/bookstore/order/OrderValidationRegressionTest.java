package com.rbdip.bookstore.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.rbdip.bookstore.reference.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

class OrderValidationRegressionTest extends AbstractIntegrationTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void rejectsMissingCustomerName(String name) {
        assertRejected(new CreateOrderRequest(name, "Moscow", null, null, null,
                List.of(new CreateOrderRequest.Item(1L, 1))));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void rejectsMissingAddress(String address) {
        assertRejected(new CreateOrderRequest("Ivan", address, null, null, null,
                List.of(new CreateOrderRequest.Item(1L, 1))));
    }

    @Test
    void rejectsMissingItems() {
        assertRejected(new CreateOrderRequest("Ivan", "Moscow", null, null, null, null));
    }

    @Test
    void rejectsUnknownProduct() {
        assertRejected(new CreateOrderRequest("Ivan", "Moscow", null, null, null,
                List.of(new CreateOrderRequest.Item(1L, 1))));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsNonPositiveQuantity(int quantity) {
        Long productId = createProduct();
        assertRejected(new CreateOrderRequest("Ivan", "Moscow", null, null, null,
                List.of(new CreateOrderRequest.Item(productId, quantity))));
    }

    @Test
    void defaultsQuantityAndPreservesContactDetails() {
        Long productId = createProduct();
        var response = restTemplate.postForEntity("/orders",
                new CreateOrderRequest("Ivan", "Moscow", "+7000", null, null,
                        List.of(new CreateOrderRequest.Item(productId, null))), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(jdbcTemplate.queryForObject("SELECT quantity FROM order_items", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForMap(
                """
                SELECT concat_ws(' ', c.first_name, c.last_name) AS customer_full_name, c.address AS customer_address,
                       c.phone AS customer_phone, o.status
                FROM orders o JOIN customers c ON c.id = o.customer_id
                """))
                .containsEntry("customer_full_name", "Ivan")
                .containsEntry("customer_address", "Moscow")
                .containsEntry("customer_phone", "+7000")
                .containsEntry("status", "new");
    }

    private void assertRejected(CreateOrderRequest request) {
        var response = restTemplate.postForEntity("/orders", request, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM orders", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM order_items", Long.class)).isZero();
    }

    private Long createProduct() {
        var response = restTemplate.postForEntity("/products", Map.of("name", "Book", "price", 100), Map.class);
        return Long.valueOf(response.getBody().get("id").toString());
    }
}
