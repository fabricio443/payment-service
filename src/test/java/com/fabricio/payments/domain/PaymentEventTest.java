package com.fabricio.payments.domain;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;

import com.fabricio.payments.domain.event.PaymentEvent;
import com.fabricio.payments.domain.event.PaymentEventType;

class PaymentEventTest {

    @Test
    void shouldMapPaymentEventEntityFields() {
        UUID paymentId = UUID.randomUUID();
        Instant createdAt = Instant.now();

        PaymentEvent event = new PaymentEvent(
                UUID.randomUUID(),
                paymentId,
                PaymentEventType.PAYMENT_CREATED,
                "{\"customerId\":\"customer-123\"}",
                createdAt
        );

        assertNotNull(event.getId());
        assertEquals(paymentId, event.getPaymentId());
        assertEquals(PaymentEventType.PAYMENT_CREATED, event.getEventType());
        assertEquals("{\"customerId\":\"customer-123\"}", event.getPayload());
        assertEquals(createdAt, event.getCreatedAt());
    }
}
