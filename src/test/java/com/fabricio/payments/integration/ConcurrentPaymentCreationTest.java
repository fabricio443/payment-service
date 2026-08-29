package com.fabricio.payments.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpResponse;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fabricio.payments.domain.Payment;
import com.fabricio.payments.repository.PaymentRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ConcurrentPaymentCreationTest {

    private static final int REQUEST_COUNT = 100;
    private static final String IDEMPOTENCY_KEY = "concurrent-payment-key-001";
    private static final String CUSTOMER_ID = "customer-123";
    private static final BigDecimal AMOUNT = new BigDecimal("55.00");

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("payments_test")
            .withUsername("testuser")
            .withPassword("testpass");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private PaymentRepository paymentRepository;

    @BeforeAll
    static void setUp() {
        postgres.start();
    }

    @AfterAll
    static void tearDown() {
        postgres.stop();
    }

    @Test
    void shouldPersistOnlyOnePaymentForSameIdempotencyKeyAcrossConcurrentRequests() throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        String baseUrl = "http://localhost:" + port + "/payments";
        ExecutorService executor = Executors.newFixedThreadPool(32);
        List<CompletableFuture<HttpResponse<String>>> futures = new ArrayList<>();

        for (int i = 0; i < REQUEST_COUNT; i++) {
            futures.add(CompletableFuture.supplyAsync(() -> sendPaymentRequest(client, baseUrl), executor));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(60, TimeUnit.SECONDS);
        executor.shutdown();

        List<HttpResponse<String>> responses = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        long http500Count = responses.stream()
                .filter(response -> response.statusCode() == HttpURLConnection.HTTP_INTERNAL_ERROR)
                .count();

        assertThat(http500Count).isZero();

        List<Payment> persistedPayments = paymentRepository.findAll();
        assertThat(persistedPayments)
                .hasSize(1)
                .allSatisfy(payment -> {
                    assertThat(payment.getCustomerId()).isEqualTo(CUSTOMER_ID);
                    assertThat(payment.getAmount()).isEqualByComparingTo(AMOUNT);
                });

        assertThat(persistedPayments.stream()
                .map(Payment::getCustomerId)
                .distinct())
                .containsExactly(CUSTOMER_ID);

        assertThat(responses)
                .allSatisfy(response -> assertThat(response.statusCode()).isIn(200, 201));
    }

    private HttpResponse<String> sendPaymentRequest(HttpClient client, String baseUrl) {
        String requestBody = "{\"customerId\":\"" + CUSTOMER_ID + "\",\"amount\":" + AMOUNT + "}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        try {
            return client.send(request, BodyHandlers.ofString());
        } catch (Exception e) {
            throw new IllegalStateException("Request failed while creating payment concurrently", e);
        }
    }
}
