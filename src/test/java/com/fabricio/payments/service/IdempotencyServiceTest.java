package com.fabricio.payments.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fabricio.payments.domain.IdempotencyKey;
import com.fabricio.payments.domain.Payment;
import com.fabricio.payments.domain.PaymentStatus;
import com.fabricio.payments.dto.PaymentMapper;
import com.fabricio.payments.dto.PaymentResponse;
import com.fabricio.payments.repository.IdempotencyKeyRepository;
import com.fabricio.payments.repository.PaymentRepository;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentMapper paymentMapper;

    private IdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        idempotencyService = new IdempotencyService(idempotencyKeyRepository, paymentRepository, paymentMapper);
    }

    @Test
    void shouldReturnCachedResponseWhenIdempotencyKeyAlreadyExists() {
        String key = "payment-key-123";
        UUID paymentId = UUID.randomUUID();
        Instant createdAt = Instant.now();
        Payment payment = new Payment(paymentId, "customer-123", new BigDecimal("99.99"), PaymentStatus.PENDING);
        payment.setCreatedAt(createdAt);
        payment.setUpdatedAt(createdAt);
        PaymentResponse response = new PaymentResponse(paymentId, "customer-123", new BigDecimal("99.99"), PaymentStatus.PENDING, createdAt);
        IdempotencyKey existing = new IdempotencyKey(key, "cached-response", 201, paymentId, createdAt);

        when(idempotencyKeyRepository.findByKey(key)).thenReturn(existing);
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(paymentMapper.toResponse(payment)).thenReturn(response);

        PaymentResponse result = idempotencyService.getOrCreate(key, () -> response);

        assertNotNull(result);
        assertEquals(response.id(), result.id());
        assertEquals(response.customerId(), result.customerId());
    }

    @Test
    void shouldPersistIdempotencyRecordWhenKeyIsNew() {
        String key = "payment-key-456";
        UUID paymentId = UUID.randomUUID();
        Instant createdAt = Instant.now();
        PaymentResponse response = new PaymentResponse(paymentId, "customer-123", new BigDecimal("12.34"), PaymentStatus.PENDING, createdAt);

        when(idempotencyKeyRepository.findByKey(key)).thenReturn(null);
        when(idempotencyKeyRepository.saveAndFlush(any(IdempotencyKey.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse result = idempotencyService.getOrCreate(key, () -> response);

        assertNotNull(result);
        assertEquals(response.id(), result.id());
    }
}
