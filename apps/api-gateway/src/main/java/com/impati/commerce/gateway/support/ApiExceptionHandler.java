package com.impati.commerce.gateway.support;

import com.impati.commerce.common.ApiContracts.ErrorResponse;
import com.impati.commerce.common.DomainException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(DomainException.class)
    ResponseEntity<ErrorResponse> domain(DomainException exception) {
        return ResponseEntity.status(exception.status())
                .body(new ErrorResponse(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(RestClientResponseException.class)
    ResponseEntity<String> downstream(RestClientResponseException exception) {
        return ResponseEntity.status(exception.getStatusCode())
                .body(exception.getResponseBodyAsString());
    }
}

