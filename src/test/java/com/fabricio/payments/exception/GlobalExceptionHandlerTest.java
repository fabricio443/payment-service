package com.fabricio.payments.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fabricio.payments.controller.PaymentCommandController;
import com.fabricio.payments.controller.PaymentQueryController;
import com.fabricio.payments.dto.CreatePaymentRequest;
import com.fabricio.payments.dto.PaymentResponse;
import com.fabricio.payments.service.PaymentCommandService;
import com.fabricio.payments.service.PaymentQueryService;

@WebMvcTest(controllers = {PaymentQueryController.class, PaymentCommandController.class})
@Import(GlobalExceptionHandler.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentQueryService paymentQueryService;

    @MockitoBean
    private PaymentCommandService paymentCommandService;

    @Test
    void shouldReturnErrorResponseWhenResourceNotFound() throws Exception {
        UUID paymentId = UUID.randomUUID();
        when(paymentQueryService.findById(paymentId)).thenThrow(new ResourceNotFoundException("Payment not found"));

        String responseBody = mockMvc.perform(get("/payments/{id}", paymentId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Payment not found"))
                .andExpect(jsonPath("$.path").value("/payments/" + paymentId))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(responseBody)
                .doesNotContain("stack_trace")
                .doesNotContain("Caused by")
                .doesNotContain("java.lang")
                .doesNotContain("org.springframework");
    }

    @Test
    void shouldReturnBadRequestForValidationErrors() throws Exception {
        mockMvc.perform(post("/payments")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\" \",\"amount\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message", containsString("customerId")))
                .andExpect(jsonPath("$.path").value("/payments"));
    }

    @Test
    void shouldReturnConflictForIdempotencyError() throws Exception {
        when(paymentCommandService.createPayment(any(CreatePaymentRequest.class), eq("key-1")))
                .thenThrow(new IdempotencyConflictException("Payment already exists for this idempotency key"));

        mockMvc.perform(post("/payments")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"customer-123\",\"amount\":55.00}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Payment already exists for this idempotency key"));
    }

    @Test
    void shouldReturnConflictForDataIntegrityViolation() throws Exception {
        when(paymentCommandService.createPayment(any(CreatePaymentRequest.class), eq("key-2")))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        mockMvc.perform(post("/payments")
                        .header("Idempotency-Key", "key-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"customer-123\",\"amount\":55.00}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("A data integrity conflict occurred."));
    }

    @Test
    void shouldReturnInternalServerErrorForUnexpectedExceptions() throws Exception {
        when(paymentCommandService.createPayment(any(CreatePaymentRequest.class), eq("key-3")))
                .thenThrow(new IllegalStateException("Unexpected failure"));

        String responseBody = mockMvc.perform(post("/payments")
                        .header("Idempotency-Key", "key-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"customer-123\",\"amount\":55.00}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("An unexpected internal error occurred."))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(responseBody)
                .doesNotContain("Unexpected failure")
                .doesNotContain("IllegalStateException")
                .doesNotContain("stack_trace");
    }

    @Test
    void shouldReturnBusinessExceptionErrorResponse() {
        BusinessException exception = new BusinessException("Payment cannot be processed");

        assertThat(exception.getMessage()).isEqualTo("Payment cannot be processed");
        assertThat(exception).isInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldCreateResourceNotFoundException() {
        ResourceNotFoundException exception = new ResourceNotFoundException("Payment not found");

        assertThat(exception.getMessage()).isEqualTo("Payment not found");
    }

    @Test
    void shouldCreateIdempotencyConflictException() {
        IdempotencyConflictException exception = new IdempotencyConflictException("Duplicate idempotency key");

        assertThat(exception.getMessage()).isEqualTo("Duplicate idempotency key");
    }
}
