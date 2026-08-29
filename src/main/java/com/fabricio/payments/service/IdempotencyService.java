package com.fabricio.payments.service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fabricio.payments.domain.IdempotencyKey;
import com.fabricio.payments.dto.PaymentMapper;
import com.fabricio.payments.dto.PaymentResponse;
import com.fabricio.payments.repository.IdempotencyKeyRepository;
import com.fabricio.payments.repository.PaymentRepository;

@Service
public class IdempotencyService {

    private static final ConcurrentMap<String, ReentrantLock> KEY_LOCKS = new ConcurrentHashMap<>();

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

    @Transactional
    public PaymentResponse getOrCreate(String key, Supplier<PaymentResponse> paymentSupplier) {
        if (key == null || key.isBlank()) {
            return paymentSupplier.get();
        }

        ReentrantLock lock = KEY_LOCKS.computeIfAbsent(key, ignored -> new ReentrantLock());
        lock.lock();
        try {
            IdempotencyKey existing = idempotencyKeyRepository.findByKey(key);
            if (existing != null) {
                return resolveResponse(existing);
            }

            PaymentResponse response = paymentSupplier.get();
            Instant now = Instant.now();

            try {
                IdempotencyKey idempotencyKey = new IdempotencyKey(
                        key,
                        response.toString(),
                        201,
                        response.id(),
                        now
                );
                idempotencyKeyRepository.saveAndFlush(idempotencyKey);
            } catch (DataIntegrityViolationException ex) {
                IdempotencyKey duplicate = idempotencyKeyRepository.findByKey(key);
                if (duplicate != null) {
                    return resolveResponse(duplicate);
                }
                throw ex;
            }

            return response;
        } finally {
            lock.unlock();
            KEY_LOCKS.remove(key, lock);
        }
    }

    private PaymentResponse resolveResponse(IdempotencyKey idempotencyKey) {
        return paymentRepository.findById(idempotencyKey.getPaymentId())
                .map(paymentMapper::toResponse)
                .orElseThrow(() -> new IllegalStateException("Payment not found for idempotency key: " + idempotencyKey.getKey()));
    }
}
