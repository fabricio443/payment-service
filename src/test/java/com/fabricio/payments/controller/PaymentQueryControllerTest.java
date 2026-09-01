package com.fabricio.payments.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricio.payments.domain.PaymentStatus;
import com.fabricio.payments.dto.PaymentResponse;
import com.fabricio.payments.exception.ResourceNotFoundException;
import com.fabricio.payments.application.query.PaymentQueryService;

@WebMvcTest(PaymentQueryController.class)
class PaymentQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentQueryService paymentQueryService;

    @Test
    void shouldReturnPaymentById() throws Exception {
        UUID paymentId = UUID.randomUUID();
        PaymentResponse response = new PaymentResponse(paymentId, "customer-123", new BigDecimal("55.00"), PaymentStatus.PENDING, null);

        when(paymentQueryService.findById(paymentId)).thenReturn(response);

        mockMvc.perform(get("/payments/{id}", paymentId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(paymentId.toString())))
                .andExpect(jsonPath("$.customerId", is("customer-123")))
                .andExpect(jsonPath("$.amount", is(55.00)))
                .andExpect(jsonPath("$.status", is("PENDING")));
    }

    @Test
    void shouldReturnNotFoundWhenPaymentDoesNotExist() throws Exception {
        UUID paymentId = UUID.randomUUID();

        when(paymentQueryService.findById(paymentId)).thenThrow(new ResourceNotFoundException("Payment not found"));

        mockMvc.perform(get("/payments/{id}", paymentId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnPaymentsByCustomerWithPaginationParameters() throws Exception {
        UUID paymentId = UUID.randomUUID();
        PaymentResponse response = new PaymentResponse(paymentId, "customer-123", new BigDecimal("77.00"), PaymentStatus.APPROVED, null);
        Page<PaymentResponse> page = new PageImpl<>(List.of(response), org.springframework.data.domain.PageRequest.of(1, 10), 1);

        when(paymentQueryService.findByCustomer(eq("customer-123"), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/customers/{customerId}/payments", "customer-123")
                        .param("page", "1")
                        .param("size", "10")
                        .param("sort", "createdAt,desc")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].customerId", is("customer-123")))
                .andExpect(jsonPath("$.content.length()", is(1)))
                .andExpect(jsonPath("$.pageable.pageNumber", is(1)))
                .andExpect(jsonPath("$.pageable.pageSize", is(10)));
    }
}
