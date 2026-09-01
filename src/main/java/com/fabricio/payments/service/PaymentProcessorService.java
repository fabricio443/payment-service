package com.fabricio.payments.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fabricio.payments.domain.Payment;
import com.fabricio.payments.domain.PaymentStatus;
import com.fabricio.payments.domain.event.PaymentEventType;
import com.fabricio.payments.event.PaymentDomainEvent;
import com.fabricio.payments.repository.PaymentRepository;

@Service
public class PaymentProcessorService {

    private final PaymentRepository paymentRepository;
    private final PaymentEventService paymentEventService;

    public PaymentProcessorService(PaymentRepository paymentRepository, PaymentEventService paymentEventService) {
        this.paymentRepository = paymentRepository;
        this.paymentEventService = paymentEventService;
    }

    @Transactional
    public void process(Payment payment, PaymentDomainEvent event) {
        PaymentStatus nextStatus = shouldApprove(payment) ? PaymentStatus.APPROVED : PaymentStatus.REJECTED;

        if (nextStatus == PaymentStatus.APPROVED) {
            payment.approve();
            paymentEventService.recordAndPublish(
                    payment.getId(),
                    PaymentEventType.PAYMENT_APPROVED,
                    String.format("{\"paymentId\":\"%s\",\"status\":\"APPROVED\",\"processedAt\":\"%s\"}",
                            payment.getId(), Instant.now())
            );
            paymentRepository.save(payment);
            return;
        }

        payment.reject();
        paymentEventService.recordAndPublish(
                payment.getId(),
                PaymentEventType.PAYMENT_REJECTED,
                String.format("{\"paymentId\":\"%s\",\"status\":\"REJECTED\",\"processedAt\":\"%s\"}",
                        payment.getId(), Instant.now())
        );
        paymentRepository.save(payment);
    }

    public boolean shouldApprove(Payment payment) {
        return payment.getAmount().compareTo(new java.math.BigDecimal("100.00")) < 0;
    }
}
