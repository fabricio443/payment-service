package com.fabricio.payments.controller;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricio.payments.domain.PaymentStatus;
import com.fabricio.payments.dto.CreatePaymentRequest;
import com.fabricio.payments.dto.PaymentResponse;
import com.fabricio.payments.service.PaymentCommandService;

@WebMvcTest(PaymentCommandController.class)
class PaymentCommandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentCommandService paymentCommandService;

    @Test
    void shouldCreatePaymentAndReturnCreatedResponse() throws Exception {
        UUID paymentId = UUID.randomUUID();
        PaymentResponse response = new PaymentResponse(paymentId, "customer-123", new BigDecimal("55.00"), PaymentStatus.PENDING, null);

        when(paymentCommandService.createPayment(any(CreatePaymentRequest.class))).thenReturn(response);

        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"customer-123\",\"amount\":55.00}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/payments/" + paymentId))
                .andExpect(jsonPath("$.customerId", is("customer-123")))
                .andExpect(jsonPath("$.amount", is(55.00)))
                .andExpect(jsonPath("$.status", is("PENDING")));
    }
}
