package com.fabricio.payments.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fabricio.payments.domain.PaymentStatus;

public record PaymentResponse(
        UUID id,
        String customerId,
        BigDecimal amount,
        PaymentStatus status,
        Instant createdAt
) {
}
