package com.fabricio.payments.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fabricio.payments.domain.event.PaymentEvent;
import com.fabricio.payments.domain.event.PaymentEventType;
import com.fabricio.payments.event.PaymentDomainEvent;
import com.fabricio.payments.repository.PaymentEventRepository;

@Service
public class PaymentEventService {

    private final PaymentEventRepository paymentEventRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    public PaymentEventService(PaymentEventRepository paymentEventRepository,
            ApplicationEventPublisher applicationEventPublisher) {
        this.paymentEventRepository = paymentEventRepository;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Transactional
    public void recordAndPublish(UUID paymentId, PaymentEventType eventType, String payload) {
        PaymentEvent paymentEvent = new PaymentEvent(
                UUID.randomUUID(),
                paymentId,
                eventType,
                payload,
                Instant.now()
        );

        paymentEventRepository.saveAndFlush(paymentEvent);
        applicationEventPublisher.publishEvent(new PaymentDomainEvent(paymentId, eventType, payload, paymentEvent.getCreatedAt()));
    }
}
