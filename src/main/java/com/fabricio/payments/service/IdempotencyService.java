package com.fabricio.payments.service;

import java.time.Instant;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.fabricio.payments.domain.IdempotencyKey;
import com.fabricio.payments.dto.PaymentMapper;
import com.fabricio.payments.dto.PaymentResponse;
import com.fabricio.payments.repository.IdempotencyKeyRepository;
import com.fabricio.payments.repository.PaymentRepository;

@Service
public class IdempotencyService {

    private static final int CREATED_STATUS_CODE = 201;

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;

    public IdempotencyService(IdempotencyKeyRepository idempotencyKeyRepository,
            PaymentRepository paymentRepository,
            PaymentMapper paymentMapper) {
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.paymentRepository = paymentRepository;
        this.paymentMapper = paymentMapper;
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public PaymentResponse getOrCreate(String key, Supplier<PaymentResponse> paymentSupplier) {
        if (key == null || key.isBlank()) {
            return paymentSupplier.get();
        }

        int inserted = idempotencyKeyRepository.insertIfAbsent(key, Instant.now());
        if (inserted == 1) {
            return finalizeWinner(key, paymentSupplier);
        }

        IdempotencyKey existing = idempotencyKeyRepository.findByKeyForUpdate(key);
        return resolveExistingKey(existing);
    }

    private PaymentResponse finalizeWinner(String key, Supplier<PaymentResponse> paymentSupplier) {
        PaymentResponse response = paymentSupplier.get();
        IdempotencyKey idempotencyKey = idempotencyKeyRepository.findByKeyForUpdate(key);
        if (idempotencyKey == null) {
            throw new IllegalStateException("Idempotency key was created but could not be loaded: " + key);
        }

        idempotencyKey.setResponseBody(response.toString());
        idempotencyKey.setStatusCode(CREATED_STATUS_CODE);
        idempotencyKey.setPaymentId(response.id());
        idempotencyKeyRepository.saveAndFlush(idempotencyKey);
        return response;
    }

    private PaymentResponse resolveExistingKey(IdempotencyKey existing) {
        if (existing == null) {
            throw new IllegalStateException("Idempotency key could not be resolved");
        }

        if (existing.getPaymentId() != null) {
            return resolveResponse(existing);
        }

        throw new IllegalStateException("Idempotency key is still pending: " + existing.getKey());
    }

    private PaymentResponse resolveResponse(IdempotencyKey idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.getPaymentId() == null) {
            throw new IllegalStateException("Idempotency key is still pending: " + (idempotencyKey != null ? idempotencyKey.getKey() : "<unknown>"));
        }

        return paymentRepository.findById(idempotencyKey.getPaymentId())
                .map(paymentMapper::toResponse)
                .orElseThrow(() -> new IllegalStateException("Payment not found for idempotency key: " + idempotencyKey.getKey()));
    }
}
