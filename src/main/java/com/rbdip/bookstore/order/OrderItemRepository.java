package com.rbdip.bookstore.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    // Проверяем конкретный товар и покупателя, не загружая всю историю заказов.
    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM order_items i
                JOIN orders o ON o.id = i.order_id
                JOIN customers c ON c.id = o.customer_id
                WHERE i.product_id = :productId
                  AND concat_ws(' ', c.first_name, c.last_name) = :customerName
            )
            """, nativeQuery = true)
    boolean existsPurchasedProduct(@Param("productId") Long productId, @Param("customerName") String customerName);
}
