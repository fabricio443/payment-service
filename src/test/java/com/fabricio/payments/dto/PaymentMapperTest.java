package com.fabricio.payments.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fabricio.payments.domain.Payment;
import com.fabricio.payments.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentMapperTest {

    private final PaymentMapper paymentMapper = new PaymentMapper();

    @Test
    void shouldMapPaymentToPaymentResponse() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.now();

        Payment payment = new Payment(id, "customer-123", new BigDecimal("99.90"), PaymentStatus.PENDING);
        payment.setCreatedAt(createdAt);

        PaymentResponse response = paymentMapper.toResponse(payment);

        assertNotNull(response);
        assertEquals(id, response.id());
        assertEquals("customer-123", response.customerId());
        assertEquals(new BigDecimal("99.90"), response.amount());
        assertEquals(PaymentStatus.PENDING, response.status());
        assertEquals(createdAt, response.createdAt());
    }
}
