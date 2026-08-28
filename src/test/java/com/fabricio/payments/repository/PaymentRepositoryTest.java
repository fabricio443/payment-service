package com.fabricio.payments.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fabricio.payments.domain.Payment;
import com.fabricio.payments.domain.PaymentStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@DataJpaTest
class PaymentRepositoryTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void shouldFindPaymentsByCustomerIdWithPagination() {
        Payment customerOnePayment = buildPayment("customer-1", new BigDecimal("10.00"));
        Payment customerTwoPaymentOne = buildPayment("customer-2", new BigDecimal("25.00"));
        Payment customerTwoPaymentTwo = buildPayment("customer-2", new BigDecimal("50.00"));
        Payment customerTwoPaymentThree = buildPayment("customer-2", new BigDecimal("75.00"));

        paymentRepository.saveAllAndFlush(List.of(
                customerOnePayment,
                customerTwoPaymentOne,
                customerTwoPaymentTwo,
                customerTwoPaymentThree));

        Pageable firstPage = PageRequest.of(0, 2);
        Page<Payment> firstPageResult = paymentRepository.findByCustomerId("customer-2", firstPage);

        assertNotNull(firstPageResult);
        assertEquals(2, firstPageResult.getContent().size());
        assertEquals(3, firstPageResult.getTotalElements());
        assertEquals(2, firstPageResult.getTotalPages());
        assertEquals("customer-2", firstPageResult.getContent().get(0).getCustomerId());
        assertEquals("customer-2", firstPageResult.getContent().get(1).getCustomerId());

        Pageable secondPage = PageRequest.of(1, 2);
        Page<Payment> secondPageResult = paymentRepository.findByCustomerId("customer-2", secondPage);

        assertNotNull(secondPageResult);
        assertEquals(1, secondPageResult.getContent().size());
        assertEquals(3, secondPageResult.getTotalElements());
        assertEquals("customer-2", secondPageResult.getContent().get(0).getCustomerId());
    }

    private Payment buildPayment(String customerId, BigDecimal amount) {
        return new Payment(
                UUID.randomUUID(),
                customerId,
                amount,
                PaymentStatus.PENDING
        );
    }
}
