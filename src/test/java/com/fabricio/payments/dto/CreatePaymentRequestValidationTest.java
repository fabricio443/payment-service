package com.fabricio.payments.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.Set;
import jakarta.validation.ConstraintViolation;
import org.junit.jupiter.api.Test;

class CreatePaymentRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldValidateRequiredFields() {
        CreatePaymentRequest invalidRequest = new CreatePaymentRequest("", new BigDecimal("10.00"));

        Set<ConstraintViolation<CreatePaymentRequest>> violations = validator.validate(invalidRequest);

        assertEquals(1, violations.size());
        assertEquals("customerId is required", violations.iterator().next().getMessage());
    }

    @Test
    void shouldValidatePositiveAmount() {
        CreatePaymentRequest invalidRequest = new CreatePaymentRequest("customer-123", BigDecimal.ZERO);

        Set<ConstraintViolation<CreatePaymentRequest>> violations = validator.validate(invalidRequest);

        assertEquals(1, violations.size());
        assertEquals("amount must be positive", violations.iterator().next().getMessage());
    }

    @Test
    void shouldAcceptValidRequest() {
        CreatePaymentRequest request = new CreatePaymentRequest("customer-123", new BigDecimal("10.00"));

        Set<ConstraintViolation<CreatePaymentRequest>> violations = validator.validate(request);

        assertEquals(0, violations.size());
    }
}
