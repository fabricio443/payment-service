package com.fabricio.payments.service;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fabricio.payments.domain.Payment;
import com.fabricio.payments.domain.PaymentStatus;
import com.fabricio.payments.domain.event.PaymentEventType;
import com.fabricio.payments.event.PaymentDomainEvent;
import com.fabricio.payments.repository.PaymentRepository;

@ExtendWith(MockitoExtension.class)
class PaymentProcessorServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentEventService paymentEventService;

    @InjectMocks
    private PaymentProcessorService paymentProcessorService;

    @Test
    void shouldApprovePaymentsBelowOneHundred() {
        Payment payment = new Payment(UUID.randomUUID(), "customer-123", new BigDecimal("99.90"), PaymentStatus.PENDING);

        assertTrue(paymentProcessorService.shouldApprove(payment));
    }

    @Test
    void shouldRejectPaymentsAboveOrEqualToOneHundred() {
        Payment payment = new Payment(UUID.randomUUID(), "customer-123", new BigDecimal("100.00"), PaymentStatus.PENDING);

        assertFalse(paymentProcessorService.shouldApprove(payment));
    }

    @Test
    void shouldApproveAndRecordApprovedEvent() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = new Payment(paymentId, "customer-123", new BigDecimal("50.00"), PaymentStatus.PENDING);
        PaymentDomainEvent event = new PaymentDomainEvent(paymentId, PaymentEventType.PAYMENT_CREATED, "{}", java.time.Instant.now());

        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        paymentProcessorService.process(payment, event);

        assertEquals(PaymentStatus.APPROVED, payment.getStatus());
        verify(paymentEventService).recordAndPublish(eq(paymentId), eq(PaymentEventType.PAYMENT_APPROVED), any());
        verify(paymentRepository).save(payment);
    }

    @Test
    void shouldRejectAndRecordRejectedEvent() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = new Payment(paymentId, "customer-123", new BigDecimal("150.00"), PaymentStatus.PENDING);
        PaymentDomainEvent event = new PaymentDomainEvent(paymentId, PaymentEventType.PAYMENT_CREATED, "{}", java.time.Instant.now());

        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        paymentProcessorService.process(payment, event);

        assertEquals(PaymentStatus.REJECTED, payment.getStatus());
        verify(paymentEventService).recordAndPublish(eq(paymentId), eq(PaymentEventType.PAYMENT_REJECTED), any());
        verify(paymentRepository).save(payment);
    }
}
