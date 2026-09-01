package com.fabricio.payments.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
    void shouldBypassIdempotencyWhenKeyIsBlank() {
        String key = "   ";
        PaymentResponse response = paymentResponse(UUID.randomUUID(), "customer-123");

        PaymentResponse result = idempotencyService.getOrCreate(key, () -> response);

        assertEquals(response, result);
        verify(idempotencyKeyRepository, never()).insertIfAbsent(any(), any());
    }

    @Test
    void shouldCreatePaymentWhenKeyIsNew() {
        String key = "payment-key-456";
        PaymentResponse response = paymentResponse(UUID.randomUUID(), "customer-123");
        IdempotencyKey pendingKey = new IdempotencyKey(key, null, null, null, Instant.now());

        when(idempotencyKeyRepository.insertIfAbsent(eq(key), any(Instant.class))).thenReturn(1);
        when(idempotencyKeyRepository.findByKeyForUpdate(key)).thenReturn(pendingKey);
        when(idempotencyKeyRepository.saveAndFlush(any(IdempotencyKey.class))).thenAnswer(invocation -> {
            IdempotencyKey saved = invocation.getArgument(0);
            saved.setResponseBody(response.toString());
            saved.setStatusCode(201);
            saved.setPaymentId(response.id());
            return saved;
        });

        PaymentResponse result = idempotencyService.getOrCreate(key, () -> response);

        assertNotNull(result);
        assertEquals(response.id(), result.id());
        verify(idempotencyKeyRepository).saveAndFlush(any(IdempotencyKey.class));
        assertEquals(response.id(), pendingKey.getPaymentId());
        assertEquals(201, pendingKey.getStatusCode());
        assertEquals(response.toString(), pendingKey.getResponseBody());
    }

    @Test
    void shouldReuseExistingPaymentWhenIdempotencyKeyAlreadyExists() {
        String key = "payment-key-123";
        UUID paymentId = UUID.randomUUID();
        Payment payment = new Payment(paymentId, "customer-123", new BigDecimal("99.99"), PaymentStatus.PENDING);
        PaymentResponse response = paymentResponse(paymentId, "customer-123");
        IdempotencyKey existing = new IdempotencyKey(key, response.toString(), 201, paymentId, Instant.now());

        when(idempotencyKeyRepository.insertIfAbsent(eq(key), any(Instant.class))).thenReturn(0);
        when(idempotencyKeyRepository.findByKeyForUpdate(key)).thenReturn(existing);
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(paymentMapper.toResponse(payment)).thenReturn(response);

        PaymentResponse result = idempotencyService.getOrCreate(key, () -> paymentResponse(UUID.randomUUID(), "customer-456"));

        assertNotNull(result);
        assertEquals(response.id(), result.id());
        assertEquals(response.customerId(), result.customerId());
    }

    @Test
    void shouldRejectAlreadyRegisteredKeyWhenPaymentRecordIsIncomplete() {
        String key = "payment-key-789";
        IdempotencyKey inconsistent = new IdempotencyKey(key, null, null, null, Instant.now());

        when(idempotencyKeyRepository.insertIfAbsent(eq(key), any(Instant.class))).thenReturn(0);
        when(idempotencyKeyRepository.findByKeyForUpdate(key)).thenReturn(inconsistent);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> idempotencyService.getOrCreate(key, () -> paymentResponse(UUID.randomUUID(), "customer-123")));

        assertEquals("Idempotency key is still pending: " + key, exception.getMessage());
    }

    @Test
    void shouldPropagateSupplierFailure() {
        String key = "payment-key-fail";
        RuntimeException failure = new RuntimeException("provider failed");

        when(idempotencyKeyRepository.insertIfAbsent(eq(key), any(Instant.class))).thenReturn(1);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> idempotencyService.getOrCreate(key, () -> {
                    throw failure;
                }));

        assertEquals("provider failed", exception.getMessage());
        verify(idempotencyKeyRepository, never()).saveAndFlush(any(IdempotencyKey.class));
    }

    private PaymentResponse paymentResponse(UUID paymentId, String customerId) {
        Instant createdAt = Instant.now();
        return new PaymentResponse(paymentId, customerId, new BigDecimal("88.50"), PaymentStatus.PENDING, createdAt);
    }
}
