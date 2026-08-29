package com.fabricio.payments.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

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
import com.fabricio.payments.domain.PaymentStatus;
import com.fabricio.payments.domain.event.PaymentEvent;
import com.fabricio.payments.domain.event.PaymentEventType;
import com.fabricio.payments.repository.PaymentEventRepository;
import com.fabricio.payments.repository.PaymentRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AsyncPaymentProcessingIntegrationTest {

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

    @Autowired
    private PaymentEventRepository paymentEventRepository;

    @BeforeAll
    static void startContainer() {
        postgres.start();
    }

    @AfterAll
    static void stopContainer() {
        postgres.stop();
    }

    @Test
    void shouldProcessPaymentAsynchronouslyAndRegisterOutcomeEvent() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        String requestBody = "{\"customerId\":\"customer-async\",\"amount\":75.00}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/payments"))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", "async-payment-key-" + UUID.randomUUID())
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(201);

        Payment createdPayment = paymentRepository.findAll().stream()
                .filter(payment -> "customer-async".equals(payment.getCustomerId()))
                .findFirst()
                .orElseThrow();

        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    Payment payment = paymentRepository.findById(createdPayment.getId()).orElseThrow();
                    assertThat(payment.getStatus()).isIn(PaymentStatus.APPROVED, PaymentStatus.REJECTED);
                });

        Payment finalPayment = paymentRepository.findById(createdPayment.getId()).orElseThrow();
        assertThat(finalPayment.getStatus()).isEqualTo(PaymentStatus.APPROVED);

        assertThat(paymentEventRepository.findAll())
                .extracting(PaymentEvent::getEventType)
                .contains(PaymentEventType.PAYMENT_CREATED);
    }
}
