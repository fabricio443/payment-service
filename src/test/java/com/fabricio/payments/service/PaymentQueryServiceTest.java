package com.fabricio.payments.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.fabricio.payments.domain.Payment;
import com.fabricio.payments.domain.PaymentStatus;
import com.fabricio.payments.dto.PaymentMapper;
import com.fabricio.payments.dto.PaymentResponse;
import com.fabricio.payments.exception.ResourceNotFoundException;
import com.fabricio.payments.repository.PaymentRepository;

@ExtendWith(MockitoExtension.class)
class PaymentQueryServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentMapper paymentMapper;

    @InjectMocks
    private PaymentQueryService paymentQueryService;

    @Test
    void shouldFindPaymentById() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = new Payment(paymentId, "customer-123", new BigDecimal("50.00"), PaymentStatus.PENDING);
        PaymentResponse expected = new PaymentResponse(paymentId, "customer-123", new BigDecimal("50.00"), PaymentStatus.PENDING, payment.getCreatedAt());

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(paymentMapper.toResponse(payment)).thenReturn(expected);

        PaymentResponse result = paymentQueryService.findById(paymentId);

        assertNotNull(result);
        assertEquals(expected, result);
        verify(paymentRepository).findById(paymentId);
    }

    @Test
    void shouldThrowResourceNotFoundWhenPaymentDoesNotExist() {
        UUID paymentId = UUID.randomUUID();

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> paymentQueryService.findById(paymentId));
    }

    @Test
    void shouldFindPaymentsByCustomerIdWithPagination() {
        String customerId = "customer-123";
        Pageable pageable = PageRequest.of(1, 2);

        Payment firstPayment = new Payment(UUID.randomUUID(), customerId, new BigDecimal("10.00"), PaymentStatus.PENDING);
        Payment secondPayment = new Payment(UUID.randomUUID(), customerId, new BigDecimal("20.00"), PaymentStatus.APPROVED);
        Page<Payment> paymentPage = new PageImpl<>(List.of(firstPayment, secondPayment), pageable, 5);

        PaymentResponse firstResponse = new PaymentResponse(firstPayment.getId(), customerId, firstPayment.getAmount(), firstPayment.getStatus(), firstPayment.getCreatedAt());
        PaymentResponse secondResponse = new PaymentResponse(secondPayment.getId(), customerId, secondPayment.getAmount(), secondPayment.getStatus(), secondPayment.getCreatedAt());

        when(paymentRepository.findByCustomerId(customerId, pageable)).thenReturn(paymentPage);
        when(paymentMapper.toResponse(firstPayment)).thenReturn(firstResponse);
        when(paymentMapper.toResponse(secondPayment)).thenReturn(secondResponse);

        Page<PaymentResponse> result = paymentQueryService.findByCustomer(customerId, pageable);

        assertNotNull(result);
        assertEquals(2, result.getContent().size());
        assertEquals(5, result.getTotalElements());
        assertEquals(3, result.getTotalPages());
        assertEquals(pageable, result.getPageable());
        assertEquals(customerId, result.getContent().get(0).customerId());
    }
}
