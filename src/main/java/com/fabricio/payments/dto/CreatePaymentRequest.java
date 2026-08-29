package com.fabricio.payments.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreatePaymentRequest(
        @NotBlank(message = "customerId is required")
        String customerId,

        @NotNull(message = "amount is required")
        @Positive(message = "amount must be positive")
        BigDecimal amount
) {
}
