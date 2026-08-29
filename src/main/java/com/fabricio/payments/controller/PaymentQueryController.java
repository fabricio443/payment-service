package com.fabricio.payments.controller;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.fabricio.payments.dto.PaymentResponse;
import com.fabricio.payments.service.PaymentQueryService;

@RestController
public class PaymentQueryController {

    private final PaymentQueryService paymentQueryService;

    public PaymentQueryController(PaymentQueryService paymentQueryService) {
        this.paymentQueryService = paymentQueryService;
    }

    @GetMapping("/payments/{id}")
    public ResponseEntity<PaymentResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(paymentQueryService.findById(id));
    }

    @GetMapping("/customers/{customerId}/payments")
    public ResponseEntity<Page<PaymentResponse>> getByCustomer(@PathVariable String customerId, Pageable pageable) {
        return ResponseEntity.ok(paymentQueryService.findByCustomer(customerId, pageable));
    }
}
