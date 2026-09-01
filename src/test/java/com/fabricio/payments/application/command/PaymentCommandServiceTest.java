package com.fabricio.payments.application.command;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fabricio.payments.domain.Payment;
import com.fabricio.payments.domain.PaymentStatus;
import com.fabricio.payments.domain.event.PaymentEventType;
import com.fabricio.payments.dto.CreatePaymentRequest;
import com.fabricio.payments.dto.PaymentMapper;
import com.fabricio.payments.dto.PaymentResponse;
import com.fabricio.payments.repository.PaymentRepository;
import com.fabricio.payments.service.IdempotencyService;
import com.fabricio.payments.service.PaymentEventService;

@ExtendWith(MockitoExtension.class)
class PaymentCommandServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentMapper paymentMapper;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private PaymentEventService paymentEventService;

    @InjectMocks
    private PaymentCommandService paymentCommandService;

    @Test
    void shouldCreatePaymentWithPendingStatusAndPersistIt() {
        CreatePaymentRequest request = new CreatePaymentRequest("customer-123", new BigDecimal("50.00"));
        String idempotencyKey = "payment-key-123";
        Payment payment = new Payment(UUID.randomUUID(), "customer-123", new BigDecimal("50.00"), PaymentStatus.PENDING);
        PaymentResponse response = new PaymentResponse(payment.getId(), "customer-123", new BigDecimal("50.00"), PaymentStatus.PENDING, payment.getCreatedAt());

        when(idempotencyService.getOrCreate(eq(idempotencyKey), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<PaymentResponse> supplier = invocation.getArgument(1);
            return supplier.get();
        });
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenReturn(payment);
        when(paymentMapper.toResponse(payment)).thenReturn(response);

        PaymentResponse result = paymentCommandService.createPayment(request, idempotencyKey);

        assertNotNull(result);
        assertEquals(PaymentStatus.PENDING, result.status());
        assertEquals("customer-123", result.customerId());
        verify(idempotencyService).getOrCreate(eq(idempotencyKey), any());
        verify(paymentEventService).recordAndPublish(eq(payment.getId()), eq(PaymentEventType.PAYMENT_CREATED), any());
    }
}
