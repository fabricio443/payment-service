package com.fabricio.payments.event;

import java.time.Instant;
import java.util.UUID;

import com.fabricio.payments.domain.event.PaymentEventType;

public record PaymentDomainEvent(
        UUID paymentId,
        PaymentEventType eventType,
        String payload,
        Instant createdAt
) {
}
