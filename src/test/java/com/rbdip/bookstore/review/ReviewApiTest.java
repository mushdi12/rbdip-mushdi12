package com.rbdip.bookstore.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.rbdip.bookstore.order.CreateOrderRequest;
import com.rbdip.bookstore.reference.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Проверяет сохранение поведения отзывов при выделении модуля.
 */
class ReviewApiTest extends AbstractIntegrationTest {

    @Test
    void acceptsAnonymousReviewWithoutPurchaseAndNormalizesMissingComment() {
        Long productId = createProduct("Book");
        var created = restTemplate.postForEntity("/products/" + productId + "/reviews",
                Map.of("rating", 5), Map.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().get("id")).isNotNull();
        var listed = restTemplate.getForEntity("/products/" + productId + "/reviews", List.class);
        assertThat(listed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listed.getBody()).containsExactly(Map.of("authorName", "anonymous", "rating", 5, "comment", ""));
    }

    @Test
    void preservesReviewAuthorAndSeparatesProductReviewsAfterPurchase() {
        Long productId = createProduct("Book");
        Long otherProductId = createProduct("Other book");
        var order = restTemplate.postForEntity("/orders",
                new CreateOrderRequest("Ivan Petrov", "Moscow", null, null, null,
                        List.of(new CreateOrderRequest.Item(productId, 1))), Map.class);
        assertThat(order.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var created = restTemplate.postForEntity("/products/" + productId + "/reviews",
                Map.of("authorName", "Ivan Petrov", "rating", 4, "comment", "Useful"), Map.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var listed = restTemplate.getForEntity("/products/" + productId + "/reviews", List.class);
        assertThat(listed.getBody()).containsExactly(Map.of(
                "authorName", "Ivan Petrov", "rating", 4, "comment", "Useful"));
        assertThat(restTemplate.getForEntity("/products/" + otherProductId + "/reviews", List.class).getBody())
                .isEmpty();
    }

    private Long createProduct(String name) {
        var created = restTemplate.postForEntity("/products", Map.of("name", name, "price", 100), Map.class);
        return Long.valueOf(created.getBody().get("id").toString());
    }
}
