package com.fabricio.payments.service;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.fabricio.payments.domain.event.PaymentEvent;
import com.fabricio.payments.domain.event.PaymentEventType;
import com.fabricio.payments.event.PaymentDomainEvent;
import com.fabricio.payments.repository.PaymentEventRepository;

@ExtendWith(MockitoExtension.class)
class PaymentEventServiceTest {

    @Mock
    private PaymentEventRepository paymentEventRepository;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private PaymentEventService paymentEventService;

    @Test
    void shouldPersistAndPublishPaymentCreatedEvent() {
        UUID paymentId = UUID.randomUUID();
        String payload = "{\"customerId\":\"customer-123\"}";

        when(paymentEventRepository.saveAndFlush(any(PaymentEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        paymentEventService.recordAndPublish(paymentId, PaymentEventType.PAYMENT_CREATED, payload);

        ArgumentCaptor<PaymentEvent> eventCaptor = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(paymentEventRepository).saveAndFlush(eventCaptor.capture());
        assertEquals(paymentId, eventCaptor.getValue().getPaymentId());
        assertEquals(PaymentEventType.PAYMENT_CREATED, eventCaptor.getValue().getEventType());
        assertNotNull(eventCaptor.getValue().getCreatedAt());

        ArgumentCaptor<PaymentDomainEvent> domainEventCaptor = ArgumentCaptor.forClass(PaymentDomainEvent.class);
        verify(applicationEventPublisher).publishEvent(domainEventCaptor.capture());
        assertEquals(paymentId, domainEventCaptor.getValue().paymentId());
        assertEquals(PaymentEventType.PAYMENT_CREATED, domainEventCaptor.getValue().eventType());
        assertEquals(payload, domainEventCaptor.getValue().payload());
    }
}
