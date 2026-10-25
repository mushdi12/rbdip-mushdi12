package com.rbdip.bookstore.order;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // Загружаем все данные ответа одним запросом, включая заказы без позиций.
    @Query("""
            SELECT DISTINCT o FROM Order o
            JOIN FETCH o.customer
            LEFT JOIN FETCH o.items i
            LEFT JOIN FETCH i.product
            """)
    List<Order> findAllWithDetails();
}
