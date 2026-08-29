package com.fabricio.payments.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import com.fabricio.payments.domain.Payment;
import com.fabricio.payments.domain.PaymentStatus;
import com.fabricio.payments.domain.event.PaymentEvent;
import com.fabricio.payments.domain.event.PaymentEventType;

@DataJpaTest
class PaymentEventRepositoryTest {

    @Autowired
    private PaymentEventRepository paymentEventRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void shouldPersistPaymentEvent() {
        Payment payment = new Payment(
                UUID.randomUUID(),
                "customer-123",
                new BigDecimal("99.90"),
                PaymentStatus.PENDING
        );
        paymentRepository.saveAndFlush(payment);

        PaymentEvent event = new PaymentEvent(
                UUID.randomUUID(),
                payment.getId(),
                PaymentEventType.PAYMENT_CREATED,
                "{\"customerId\":\"customer-123\"}",
                Instant.now()
        );

        PaymentEvent saved = paymentEventRepository.saveAndFlush(event);

        assertNotNull(saved);
        assertEquals(event.getPaymentId(), saved.getPaymentId());
        assertEquals(PaymentEventType.PAYMENT_CREATED, saved.getEventType());
    }
}
