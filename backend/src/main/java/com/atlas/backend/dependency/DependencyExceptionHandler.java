package com.atlas.backend.dependency;

import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = DependencyController.class)
public class DependencyExceptionHandler {
    @ExceptionHandler(DependencyException.class)
    ResponseEntity<Map<String, String>> domain(DependencyException ex) {
        return ResponseEntity.status(ex.status()).body(Map.of("error_code", ex.code(), "message", ex.getMessage()));
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String, String>> malformed(Exception ex) {
        return ResponseEntity.badRequest().body(Map.of("error_code", "VALIDATION_ERROR", "message", "Malformed request or unsupported field"));
    }
}
