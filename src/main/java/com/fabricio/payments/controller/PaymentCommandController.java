package com.fabricio.payments.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fabricio.payments.dto.CreatePaymentRequest;
import com.fabricio.payments.dto.PaymentResponse;
import com.fabricio.payments.application.command.PaymentCommandService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/payments")
public class PaymentCommandController {

    private final PaymentCommandService paymentCommandService;

    public PaymentCommandController(PaymentCommandService paymentCommandService) {
        this.paymentCommandService = paymentCommandService;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> create(
            @Valid @RequestBody CreatePaymentRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        PaymentResponse response = paymentCommandService.createPayment(request, idempotencyKey);
        return ResponseEntity.created(URI.create("/payments/" + response.id())).body(response);
    }
}
