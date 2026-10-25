package com.rbdip.bookstore.customer;

import static org.assertj.core.api.Assertions.assertThat;

import com.rbdip.bookstore.BookstoreApplication;
import com.rbdip.bookstore.order.CreateOrderRequest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Проверяет переход от полного имени к двум полям на работающей БД и API.
 */
@Testcontainers
class CustomerNameMigrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private JdbcTemplate jdbc;

    @BeforeEach
    void prepareLegacySchema() {
        migration("2").clean();
        migration("2").migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    }

    @Test
    void preservesNamesAndSynchronizesOldAndNewWritersBeforeContract() {
        List<String> names = List.of("Ivan Petrov", "Anna Maria Sidorova", "Madonna", " Ivan  Petrov ", "Ivan ");
        for (String name : names) {
            jdbc.update("INSERT INTO customers (full_name, address) VALUES (?, 'seed')", name);
        }
        migration("5").migrate();
        assertThat(jdbc.queryForList("SELECT concat_ws(' ', first_name, last_name) FROM customers ORDER BY id",
                String.class)).containsExactlyElementsOf(names);

        jdbc.update("INSERT INTO customers (full_name, address) VALUES ('Old Writer', 'legacy')");
        assertThat(jdbc.queryForMap("SELECT first_name, last_name FROM customers WHERE address = 'legacy'"))
                .containsEntry("first_name", "Old").containsEntry("last_name", "Writer");
        jdbc.update("UPDATE customers SET full_name = 'Renamed Legacy' WHERE address = 'legacy'");
        assertThat(jdbc.queryForMap("SELECT first_name, last_name FROM customers WHERE address = 'legacy'"))
                .containsEntry("first_name", "Renamed").containsEntry("last_name", "Legacy");

        jdbc.update("INSERT INTO customers (first_name, last_name, address) VALUES ('New', 'Writer', 'modern')");
        assertThat(jdbc.queryForObject("SELECT full_name FROM customers WHERE address = 'modern'", String.class))
                .isEqualTo("New Writer");
        jdbc.update("UPDATE customers SET last_name = 'Renamed' WHERE address = 'modern'");
        assertThat(jdbc.queryForObject("SELECT full_name FROM customers WHERE address = 'modern'", String.class))
                .isEqualTo("New Renamed");
        migration("6").migrate();

        assertThat(jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'customers'
                """, String.class)).contains("first_name", "last_name").doesNotContain("full_name");
        assertThat(jdbc.queryForList("SELECT concat_ws(' ', first_name, last_name) FROM customers WHERE address = 'seed' ORDER BY id",
                String.class)).containsExactlyElementsOf(names);
    }

    @RepeatedTest(3)
    @Timeout(90)
    void servesLegacyQueriesDuringExpandAndHttpRequestsDuringContract() throws Exception {
        List<Exception> errors = new CopyOnWriteArrayList<>();
        AtomicBoolean running = new AtomicBoolean(true);
        CountDownLatch legacyStarted = new CountDownLatch(1);
        AtomicInteger legacyOperations = new AtomicInteger();
        try (var workers = Executors.newFixedThreadPool(2)) {
            workers.submit(() -> {
                while (running.get()) {
                    try {
                        jdbc.queryForList("SELECT full_name FROM customers");
                        jdbc.update("""
                                INSERT INTO customers (full_name, address) VALUES ('Old Writer', 'load')
                                ON CONFLICT (full_name, address, phone) DO UPDATE SET full_name = EXCLUDED.full_name
                                """);
                        legacyOperations.incrementAndGet();
                    } catch (Exception error) {
                        errors.add(error);
                    } finally {
                        legacyStarted.countDown();
                    }
                }
            });
            try {
                assertThat(legacyStarted.await(10, TimeUnit.SECONDS)).isTrue();
                migration("5").migrate();
            } finally {
                running.set(false);
            }
            workers.shutdown();
            assertThat(workers.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(errors).isEmpty();
        assertThat(legacyOperations.get()).isPositive();
        exerciseHttpDuringContract(errors);
    }

    private void exerciseHttpDuringContract(List<Exception> errors) throws Exception {
        try (var context = new SpringApplicationBuilder(BookstoreApplication.class).run(
                "--server.port=0", "--spring.flyway.target=5",
                "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--spring.jpa.properties.hibernate.generate_statistics=false")) {
            int port = ((ServletWebServerApplicationContext) context).getWebServer().getPort();
            TestRestTemplate rest = new TestRestTemplate(new RestTemplateBuilder()
                    .rootUri("http://localhost:" + port)
                    .setConnectTimeout(Duration.ofSeconds(3)).setReadTimeout(Duration.ofSeconds(3)));
            var product = rest.postForEntity("/products", Map.of("name", "Book", "price", 100), Map.class);
            assertThat(product.getStatusCode().is2xxSuccessful()).isTrue();
            Long productId = Long.valueOf(product.getBody().get("id").toString());
            AtomicBoolean running = new AtomicBoolean(true);
            AtomicBoolean contracted = new AtomicBoolean();
            CountDownLatch before = new CountDownLatch(1);
            CountDownLatch after = new CountDownLatch(1);
            AtomicInteger requests = new AtomicInteger();
            try (var worker = Executors.newSingleThreadExecutor()) {
                worker.submit(() -> {
                    while (running.get()) {
                        try {
                            boolean afterContract = contracted.get();
                            performOrderRequests(rest, productId);
                            requests.addAndGet(2);
                            (afterContract ? after : before).countDown();
                        } catch (Exception error) {
                            errors.add(error);
                            before.countDown();
                            after.countDown();
                        }
                    }
                });
                try {
                    assertThat(before.await(10, TimeUnit.SECONDS)).isTrue();
                    migration("6").migrate();
                    contracted.set(true);
                    assertThat(after.await(10, TimeUnit.SECONDS)).isTrue();
                } finally {
                    running.set(false);
                }
            }
            assertThat(errors).isEmpty();
            assertThat(requests.get()).isGreaterThanOrEqualTo(4);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM customers WHERE first_name = 'Load' AND last_name = 'Writer'",
                    Long.class)).isEqualTo(1);
        }
    }

    private void performOrderRequests(TestRestTemplate rest, Long productId) {
        var created = rest.postForEntity("/orders",
                new CreateOrderRequest("Load Writer", "Moscow", null, null, null,
                        List.of(new CreateOrderRequest.Item(productId, 1))), Map.class);
        var listed = rest.getForEntity("/orders", List.class);
        if (!created.getStatusCode().is2xxSuccessful() || !listed.getStatusCode().is2xxSuccessful()
                || listed.getBody() == null || listed.getBody().isEmpty()) {
            throw new IllegalStateException("Ошибка API под нагрузкой: "
                    + created.getStatusCode() + ", " + listed.getStatusCode());
        }
    }

    private Flyway migration(String target) {
        return Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .cleanDisabled(false).target(target).load();
    }
}
