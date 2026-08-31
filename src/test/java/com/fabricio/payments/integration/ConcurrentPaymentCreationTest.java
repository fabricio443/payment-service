package com.fabricio.payments.integration;

import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.fabricio.payments.config.AbstractIntegrationTest;
import com.fabricio.payments.domain.IdempotencyKey;
import com.fabricio.payments.domain.Payment;
import com.fabricio.payments.repository.IdempotencyKeyRepository;
import com.fabricio.payments.repository.PaymentEventRepository;
import com.fabricio.payments.repository.PaymentRepository;

class ConcurrentPaymentCreationTest extends AbstractIntegrationTest {

    private static final int REQUEST_COUNT = 100;
    private static final String CUSTOMER_ID = "customer-123";
    private static final BigDecimal AMOUNT = new BigDecimal("55.00");

    private String idempotencyKey;

    @LocalServerPort
    private int port;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentEventRepository paymentEventRepository;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @BeforeEach
    void setUp() {
        paymentEventRepository.deleteAll();
        paymentRepository.deleteAll();
        idempotencyKeyRepository.deleteAll();
        idempotencyKey = "concurrent-payment-key-" + UUID.randomUUID();
    }

    @Test
    void shouldPersistOnlyOnePaymentForSameIdempotencyKeyAcrossConcurrentRequests() throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        String baseUrl = "http://localhost:" + port + "/payments";
        ExecutorService executor = Executors.newFixedThreadPool(8);
        List<CompletableFuture<HttpResponse<String>>> futures = new ArrayList<>();

        for (int i = 0; i < REQUEST_COUNT; i++) {
            futures.add(CompletableFuture.supplyAsync(() -> sendPaymentRequest(client, baseUrl), executor));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(180, TimeUnit.SECONDS);
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

        List<IdempotencyKey> persistedIdempotencyKeys = idempotencyKeyRepository.findAll();
        assertThat(persistedIdempotencyKeys).hasSize(1);
        assertThat(persistedIdempotencyKeys.get(0).getKey()).isEqualTo(idempotencyKey);
        assertThat(persistedIdempotencyKeys.get(0).getPaymentId()).isEqualTo(persistedPayments.get(0).getId());

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
                .header("Idempotency-Key", idempotencyKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        try {
            return client.send(request, BodyHandlers.ofString());
        } catch (Exception e) {
            throw new IllegalStateException("Request failed while creating payment concurrently", e);
        }
    }
}
