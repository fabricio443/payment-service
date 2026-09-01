package com.fabricio.payments.event.listener;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.fabricio.payments.domain.Payment;
import com.fabricio.payments.domain.PaymentStatus;
import com.fabricio.payments.domain.event.PaymentEventType;
import com.fabricio.payments.event.PaymentDomainEvent;
import com.fabricio.payments.repository.PaymentRepository;
import com.fabricio.payments.service.PaymentProcessorService;

@Component
public class PaymentEventListener {

    private final PaymentProcessorService paymentProcessorService;
    private final PaymentRepository paymentRepository;

    public PaymentEventListener(PaymentProcessorService paymentProcessorService, PaymentRepository paymentRepository) {
        this.paymentProcessorService = paymentProcessorService;
        this.paymentRepository = paymentRepository;
    }

    @Async("paymentTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentCreated(PaymentDomainEvent event) {
        if (event.eventType() != PaymentEventType.PAYMENT_CREATED) {
            return;
        }

        Payment payment = paymentRepository.findById(event.paymentId())
                .orElse(null);

        if (payment == null || payment.getStatus() != PaymentStatus.PENDING) {
            return;
        }

        paymentProcessorService.process(payment, event);
    }
}
