package com.fabricio.payments.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class PaymentTest {

    @Test
    void shouldChangePaymentStatusBetweenStates() {
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setCustomerId("customer-123");
        payment.setAmount(new BigDecimal("250.00"));
        payment.setStatus(PaymentStatus.PENDING);

        payment.approve();
        assertEquals(PaymentStatus.APPROVED, payment.getStatus());

        payment.reject();
        assertEquals(PaymentStatus.REJECTED, payment.getStatus());

        payment.markPending();
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
    }

    @Test
    void shouldSetAuditFieldsOnPrePersist() {
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setCustomerId("customer-456");
        payment.setAmount(new BigDecimal("99.90"));
        payment.setStatus(PaymentStatus.PENDING);

        payment.onPrePersist();

        assertNotNull(payment.getCreatedAt());
        assertNotNull(payment.getUpdatedAt());
        assertEquals(payment.getCreatedAt(), payment.getUpdatedAt());
    }

    @Test
    void shouldUpdateUpdatedAtOnPreUpdate() throws InterruptedException {
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setCustomerId("customer-789");
        payment.setAmount(new BigDecimal("15.50"));
        payment.setStatus(PaymentStatus.PENDING);
        payment.onPrePersist();

        Instant previousUpdatedAt = payment.getUpdatedAt();
        Thread.sleep(10L);

        payment.onPreUpdate();

        assertNotNull(payment.getCreatedAt());
        assertTrue(payment.getUpdatedAt().isAfter(previousUpdatedAt));
    }
}
