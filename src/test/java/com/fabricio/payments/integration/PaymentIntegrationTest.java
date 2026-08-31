package com.fabricio.payments.integration;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.domain.PageRequest;

import com.fabricio.payments.config.AbstractIntegrationTest;
import com.fabricio.payments.domain.Payment;
import com.fabricio.payments.domain.PaymentStatus;
import com.fabricio.payments.repository.PaymentRepository;

class PaymentIntegrationTest extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void shouldCreatePaymentAndReadItBack() throws Exception {
        String customerId = "customer-integration-1";
        BigDecimal amount = new BigDecimal("75.00");
        String idempotencyKey = "payment-integration-key-1";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest createRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/payments"))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", idempotencyKey)
                .POST(HttpRequest.BodyPublishers.ofString("{\"customerId\":\"" + customerId + "\",\"amount\":" + amount + "}"))
                .build();

        HttpResponse<String> createResponse = client.send(createRequest, HttpResponse.BodyHandlers.ofString());

        assertThat(createResponse.statusCode()).isEqualTo(201);
        assertThat(createResponse.headers().firstValue("Location")).isPresent();

        String location = createResponse.headers().firstValue("Location").orElseThrow();
        UUID paymentId = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));

        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    Payment persisted = paymentRepository.findById(paymentId).orElseThrow();
                    assertThat(persisted.getCustomerId()).isEqualTo(customerId);
                    assertThat(persisted.getAmount()).isEqualByComparingTo(amount);
                    assertThat(persisted.getStatus()).isIn(PaymentStatus.APPROVED, PaymentStatus.REJECTED);
                });

        HttpRequest readRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/payments/" + paymentId))
                .header("Accept", "application/json")
                .build();

        HttpResponse<String> readResponse = client.send(readRequest, HttpResponse.BodyHandlers.ofString());

        assertThat(readResponse.statusCode()).isEqualTo(200);
        assertThat(readResponse.body()).contains("\"id\":\"" + paymentId + "\"");
        assertThat(readResponse.body()).contains("\"customerId\":\"" + customerId + "\"");
        assertThat(readResponse.body()).contains("\"status\":\"APPROVED\"");

        HttpRequest listRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/customers/" + customerId + "/payments?page=0&size=10&sort=createdAt,desc"))
                .header("Accept", "application/json")
                .build();

        HttpResponse<String> listResponse = client.send(listRequest, HttpResponse.BodyHandlers.ofString());

        assertThat(listResponse.statusCode()).isEqualTo(200);
        assertThat(listResponse.body()).contains("\"content\"");
        assertThat(listResponse.body()).contains("\"customerId\":\"" + customerId + "\"");

        List<Payment> results = paymentRepository.findByCustomerId(customerId, PageRequest.of(0, 10)).getContent();
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getCustomerId()).isEqualTo(customerId);
    }
}
