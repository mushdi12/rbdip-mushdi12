package com.rbdip.bookstore.customer;

import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Находит или создаёт клиента без дублей при параллельном оформлении заказов.
 */
@Component
public class CustomerRegistry {

    private final JdbcTemplate jdbcTemplate;
    private final EntityManager entityManager;

    public CustomerRegistry(JdbcTemplate jdbcTemplate, EntityManager entityManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.entityManager = entityManager;
    }

    public Customer findOrCreate(String fullName, String address, String phone) {
        // Имя само по себе не идентифицирует клиента: учитываем также контакты.
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO customers (full_name, address, phone) VALUES (?, ?, ?)
                ON CONFLICT (full_name, address, phone)
                DO UPDATE SET full_name = EXCLUDED.full_name
                RETURNING id
                """, Long.class, fullName, address, phone);
        return entityManager.getReference(Customer.class, id);
    }
}
