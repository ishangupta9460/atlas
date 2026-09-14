package com.atlas.backend.fixedcommitment;

import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Sanitize malformed inputs locally without changing existing domains' error behavior. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = FixedCommitmentController.class)
public class FixedCommitmentExceptionHandler {
    @ExceptionHandler(FixedCommitmentNotFoundException.class)
    ResponseEntity<Map<String, String>> notFound(FixedCommitmentNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error_code", "FIXED_COMMITMENT_NOT_FOUND", "message", ex.getMessage()));
    }

    @ExceptionHandler(InvalidFixedCommitmentException.class)
    ResponseEntity<Map<String, String>> invalid(InvalidFixedCommitmentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error_code", "VALIDATION_ERROR", "message", ex.getMessage()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String, String>> malformed(Exception ex) {
        return ResponseEntity.badRequest().body(Map.of("error_code", "VALIDATION_ERROR", "message", "Malformed request or unsupported field"));
    }
}
