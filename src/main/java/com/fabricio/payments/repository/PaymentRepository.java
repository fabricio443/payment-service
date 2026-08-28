package com.fabricio.payments.repository;

import com.fabricio.payments.domain.Payment;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Page<Payment> findByCustomerId(String customerId, Pageable pageable);
}
