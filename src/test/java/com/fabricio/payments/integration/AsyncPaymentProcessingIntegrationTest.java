package com.fabricio.payments.integration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.fabricio.payments.config.AbstractIntegrationTest;
import com.fabricio.payments.domain.Payment;
import com.fabricio.payments.domain.PaymentStatus;
import com.fabricio.payments.domain.event.PaymentEventType;
import com.fabricio.payments.repository.PaymentEventRepository;
import com.fabricio.payments.repository.PaymentRepository;

class AsyncPaymentProcessingIntegrationTest extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentEventRepository paymentEventRepository;

    @Test
    void shouldProcessPaymentAsynchronouslyAndRegisterOutcomeEvent() throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        String requestBody =
                "{\"customerId\":\"customer-async\",\"amount\":75.00}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/payments"))
                .header("Content-Type", "application/json")
                .header(
                        "Idempotency-Key",
                        "async-payment-key-" + UUID.randomUUID()
                )
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(201);

        Payment createdPayment = paymentRepository.findAll()
                .stream()
                .filter(payment ->
                        "customer-async".equals(payment.getCustomerId()))
                .findFirst()
                .orElseThrow();

        await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    Payment payment = paymentRepository
                            .findById(createdPayment.getId())
                            .orElseThrow();

                    assertThat(payment.getStatus())
                            .isIn(
                                    PaymentStatus.APPROVED,
                                    PaymentStatus.REJECTED
                            );
                });

        Payment finalPayment = paymentRepository
                .findById(createdPayment.getId())
                .orElseThrow();

        assertThat(finalPayment.getStatus())
                .isEqualTo(PaymentStatus.APPROVED);

        assertThat(paymentEventRepository.findAll())
                .anySatisfy(event -> {
                    assertThat(event.getPaymentId())
                            .isEqualTo(createdPayment.getId());

                    assertThat(event.getEventType())
                            .isEqualTo(PaymentEventType.PAYMENT_APPROVED);
                });
    }
}