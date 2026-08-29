package com.fabricio.payments.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fabricio.payments.domain.event.PaymentEvent;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, UUID> {
}
