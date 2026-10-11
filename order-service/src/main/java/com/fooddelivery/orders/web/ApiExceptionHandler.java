package com.fooddelivery.orders.web;

import com.fooddelivery.orders.service.OrderException;
import com.fooddelivery.orders.service.UpstreamUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** One place that turns exceptions into the { success:false, message } JSON the frontends expect. */
@RestControllerAdvice
class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(OrderException.class)
    ResponseEntity<ApiResponse<Void>> orderException(OrderException e) {
        if (e instanceof UpstreamUnavailableException u) {
            log.warn("Node backend unavailable: {}", u.detail(), u);
        } else if (e.status().is5xxServerError()) {
            log.error("Order request failed: {}", e.getMessage(), e);
        }
        return ResponseEntity.status(e.status()).body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiResponse<Void>> badBody(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(ApiResponse.error("Invalid request"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> unexpected(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("Error"));
    }
}
