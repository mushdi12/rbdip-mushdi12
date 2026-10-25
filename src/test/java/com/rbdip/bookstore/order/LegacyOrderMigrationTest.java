package com.rbdip.bookstore.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Проверяет совместимость старых заказов до последнего contract-шага.
 */
@Testcontainers
class LegacyOrderMigrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;

    @BeforeEach
    void prepareOriginalSchema() {
        migration("1").clean();
        migration("1").migrate();
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        jdbc.update("INSERT INTO products (name, price) VALUES ('Book', 100)");
    }

    @Test
    void supportsBothOrderFormatsAndRemovesLegacyColumnsOnlyAtContract() {
        migration("2").migrate();
        assertThat(columns("orders")).contains("customer_full_name", "customer_address", "customer_phone");
        assertThat(columns("order_items")).contains("product_name", "product_price");
        migration("5").migrate();

        Long legacyOrderId = writeLegacyOrder();
        assertThat(jdbc.queryForMap("""
                SELECT c.first_name, c.last_name, c.address FROM orders o
                JOIN customers c ON c.id = o.customer_id WHERE o.id = ?
                """, legacyOrderId)).containsEntry("first_name", "Legacy")
                .containsEntry("last_name", "Customer").containsEntry("address", "Moscow");
        jdbc.update("UPDATE orders SET customer_full_name = 'Renamed Customer' WHERE id = ?", legacyOrderId);
        assertThat(jdbc.queryForObject("""
                SELECT c.full_name FROM customers c JOIN orders o ON o.customer_id = c.id WHERE o.id = ?
                """, String.class, legacyOrderId)).isEqualTo("Renamed Customer");

        Long customerId = jdbc.queryForObject("""
                INSERT INTO customers (first_name, last_name, address, phone)
                VALUES ('New', 'Customer', 'SPb', '+7000') RETURNING id
                """, Long.class);
        Long modernOrderId = jdbc.queryForObject("INSERT INTO orders (customer_id) VALUES (?) RETURNING id",
                Long.class, customerId);
        assertThat(jdbc.queryForMap("""
                SELECT customer_full_name, customer_address, customer_phone FROM orders WHERE id = ?
                """, modernOrderId)).containsEntry("customer_full_name", "New Customer")
                .containsEntry("customer_address", "SPb").containsEntry("customer_phone", "+7000");
        jdbc.update("INSERT INTO order_items (order_id, product_id, quantity) VALUES (?, 1, 2)", modernOrderId);
        assertThat(jdbc.queryForObject("SELECT product_name FROM order_items WHERE order_id = ?",
                String.class, modernOrderId)).isEqualTo("Book");
        jdbc.update("UPDATE customers SET first_name = 'Updated' WHERE id = ?", customerId);
        assertThat(jdbc.queryForObject("SELECT customer_full_name FROM orders WHERE id = ?",
                String.class, modernOrderId)).isEqualTo("Updated Customer");
        jdbc.update("UPDATE order_items SET product_name = 'Archived', product_price = 50 WHERE order_id = ?",
                modernOrderId);
        assertThat(jdbc.queryForObject("""
                SELECT p.name FROM products p JOIN order_items i ON i.product_id = p.id WHERE i.order_id = ?
                """, String.class, modernOrderId)).isEqualTo("Archived");
        migration("6").migrate();

        assertThat(columns("orders")).contains("customer_id")
                .doesNotContain("customer_full_name", "customer_address", "customer_phone");
        assertThat(columns("order_items")).contains("product_id").doesNotContain("product_name", "product_price");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders", Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_items", Long.class)).isEqualTo(2);
    }

    @Test
    @Timeout(90)
    void legacyTransactionsKeepWorkingWhileExpandMigrationsRun() throws Exception {
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicBoolean migrated = new AtomicBoolean();
        AtomicInteger completed = new AtomicInteger();
        CountDownLatch before = new CountDownLatch(1);
        CountDownLatch after = new CountDownLatch(1);
        List<Exception> errors = new CopyOnWriteArrayList<>();
        try (var worker = Executors.newSingleThreadExecutor()) {
            worker.submit(() -> {
                while (running.get()) {
                    try {
                        boolean afterMigration = migrated.get();
                        transactions.executeWithoutResult(status -> {
                            writeLegacyOrder();
                            jdbc.queryForList("SELECT customer_full_name FROM orders LIMIT 1");
                        });
                        completed.incrementAndGet();
                        (afterMigration ? after : before).countDown();
                    } catch (Exception error) {
                        errors.add(error);
                        before.countDown();
                        after.countDown();
                    }
                }
            });
            try {
                assertThat(before.await(10, TimeUnit.SECONDS)).isTrue();
                migration("5").migrate();
                migrated.set(true);
                assertThat(after.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                running.set(false);
            }
        }
        assertThat(errors).isEmpty();
        assertThat(completed.get()).isGreaterThanOrEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders WHERE customer_id IS NULL", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_items WHERE product_id IS NULL", Long.class)).isZero();
    }

    private Long writeLegacyOrder() {
        Long id = jdbc.queryForObject("""
                INSERT INTO orders (customer_full_name, customer_address) VALUES ('Legacy Customer', 'Moscow')
                RETURNING id
                """, Long.class);
        jdbc.update("""
                INSERT INTO order_items (order_id, product_name, product_price, quantity) VALUES (?, 'Book', 100, 1)
                """, id);
        return id;
    }

    private List<String> columns(String table) {
        return jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = ?
                """, String.class, table);
    }

    private Flyway migration(String target) {
        return Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .cleanDisabled(false).target(target).load();
    }
}
