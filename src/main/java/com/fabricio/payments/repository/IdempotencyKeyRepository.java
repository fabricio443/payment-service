package com.fabricio.payments.repository;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fabricio.payments.domain.IdempotencyKey;

import jakarta.persistence.LockModeType;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {

    @Modifying
    @Query(value = "INSERT INTO idempotency_keys (idempotency_key, response_body, status_code, payment_id, created_at) " +
            "VALUES (:key, NULL, NULL, NULL, :createdAt) ON CONFLICT (idempotency_key) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("key") String key, @Param("createdAt") Instant createdAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from IdempotencyKey i where i.key = :key")
    IdempotencyKey findByKeyForUpdate(@Param("key") String key);
}