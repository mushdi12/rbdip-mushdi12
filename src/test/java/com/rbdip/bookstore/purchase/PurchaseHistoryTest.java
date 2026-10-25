package com.rbdip.bookstore.purchase;

import static org.assertj.core.api.Assertions.assertThat;

import com.rbdip.bookstore.order.CreateOrderRequest;
import com.rbdip.bookstore.reference.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

/**
 * Проверяет публичный контракт на настоящей истории покупок в PostgreSQL.
 */
class PurchaseHistoryTest extends AbstractIntegrationTest {

    @Autowired
    private PurchaseHistory purchaseHistory;

    @Test
    void matchesBothProductAndCustomerIncludingSingleWordNames() {
        var product = restTemplate.postForEntity("/products", Map.of("name", "Book", "price", 100), Map.class);
        Long productId = Long.valueOf(product.getBody().get("id").toString());
        assertThat(purchaseHistory.hasPurchased(productId, "Ivan Petrov")).isFalse();
        placeOrder(productId, "Ivan Petrov");
        placeOrder(productId, "Madonna");

        assertThat(purchaseHistory.hasPurchased(productId, "Ivan Petrov")).isTrue();
        assertThat(purchaseHistory.hasPurchased(productId, "Madonna")).isTrue();
        assertThat(purchaseHistory.hasPurchased(productId, "Other Customer")).isFalse();
        assertThat(purchaseHistory.hasPurchased(-1L, "Ivan Petrov")).isFalse();
        assertThat(purchaseHistory.hasPurchased(productId, null)).isFalse();
    }

    private void placeOrder(Long productId, String customerName) {
        var created = restTemplate.postForEntity("/orders",
                new CreateOrderRequest(customerName, "Moscow", null, null, null,
                        List.of(new CreateOrderRequest.Item(productId, 1))), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
