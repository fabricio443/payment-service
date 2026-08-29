package com.fabricio.payments.dto;

import com.fabricio.payments.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        String customerId,
        BigDecimal amount,
        PaymentStatus status,
        Instant createdAt
) {
}
