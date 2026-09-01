package com.fabricio.payments.event.listener;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fabricio.payments.domain.Payment;
import com.fabricio.payments.domain.PaymentStatus;
import com.fabricio.payments.domain.event.PaymentEventType;
import com.fabricio.payments.event.PaymentDomainEvent;
import com.fabricio.payments.repository.PaymentRepository;
import com.fabricio.payments.service.PaymentProcessorService;

@ExtendWith(MockitoExtension.class)
class PaymentEventListenerTest {

    @Mock
    private PaymentProcessorService paymentProcessorService;

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentEventListener paymentEventListener;

    @Test
    void shouldProcessOnlyCreatedEventsAfterCommit() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = new Payment(paymentId, "customer-123", new BigDecimal("40.00"), PaymentStatus.PENDING);
        PaymentDomainEvent event = new PaymentDomainEvent(paymentId, PaymentEventType.PAYMENT_CREATED, "{}", java.time.Instant.now());

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        paymentEventListener.handlePaymentCreated(event);

        verify(paymentProcessorService).process(payment, event);
    }

    @Test
    void shouldIgnoreNonCreatedEvents() {
        UUID paymentId = UUID.randomUUID();
        PaymentDomainEvent event = new PaymentDomainEvent(paymentId, PaymentEventType.PAYMENT_APPROVED, "{}", java.time.Instant.now());

        paymentEventListener.handlePaymentCreated(event);

        verify(paymentProcessorService, never()).process(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
