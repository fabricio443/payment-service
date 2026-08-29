package com.fabricio.payments.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fabricio.payments.domain.IdempotencyKey;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {

    IdempotencyKey findByKey(String key);
}