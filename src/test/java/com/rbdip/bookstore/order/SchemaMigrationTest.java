package com.rbdip.bookstore.order;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Проверяет нормализацию на заполненной БД, а не только создание пустой схемы.
 */
@Testcontainers
class SchemaMigrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void preservesExistingOrdersAndRestoresMissingCatalogProducts() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .target("1").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        seedLegacyData(jdbc);

        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load().migrate();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM customers", Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders", Long.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT customer_id) FROM orders WHERE id IN (1, 2)",
                Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_items", Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForMap("""
                SELECT c.full_name, c.address, c.phone, o.status
                FROM orders o JOIN customers c ON c.id = o.customer_id WHERE o.id = 1
                """))
                .containsEntry("full_name", "Ivan Petrov")
                .containsEntry("address", "Moscow")
                .containsEntry("phone", null)
                .containsEntry("status", "paid");
        assertThat(jdbc.queryForObject("SELECT product_id FROM order_items WHERE id = 1", Long.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForMap("""
                SELECT p.name, p.price, i.quantity
                FROM order_items i JOIN products p ON p.id = i.product_id WHERE i.id = 2
                """))
                .containsEntry("name", "Archived book")
                .containsEntry("price", new java.math.BigDecimal("50.00"))
                .containsEntry("quantity", 2);
    }

    private void seedLegacyData(JdbcTemplate jdbc) {
        jdbc.execute("""
                INSERT INTO products (name, price) VALUES ('Book', 100), ('Book', 100);
                INSERT INTO orders (customer_full_name, customer_address, customer_phone, status)
                VALUES ('Ivan Petrov', 'Moscow', NULL, 'paid'),
                       ('Ivan Petrov', 'Moscow', NULL, 'new'),
                       ('Ivan Petrov', 'SPb', '+7000', 'new');
                INSERT INTO order_items (order_id, product_name, product_price, quantity)
                VALUES (1, 'Book', 100, 1), (3, 'Archived book', 50, 2);
                """);
    }
}
