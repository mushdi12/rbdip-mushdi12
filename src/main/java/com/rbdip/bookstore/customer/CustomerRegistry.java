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
        // То же разбиение, что и в миграции: не теряем составную фамилию и пробелы.
        int separator = fullName.indexOf(' ');
        String firstName = separator < 0 ? fullName : fullName.substring(0, separator);
        String lastName = separator < 0 ? null : fullName.substring(separator + 1);
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO customers (first_name, last_name, address, phone) VALUES (?, ?, ?, ?)
                ON CONFLICT (first_name, last_name, address, phone)
                DO UPDATE SET first_name = EXCLUDED.first_name
                RETURNING id
                """, Long.class, firstName, lastName, address, phone);
        return entityManager.getReference(Customer.class, id);
    }
}
