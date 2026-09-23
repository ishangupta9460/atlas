package com.atlas.backend.execution;

import com.atlas.backend.commitment.CommitmentException;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes=ExecutionController.class)
public class ExecutionExceptionHandler {
    @ExceptionHandler(ExecutionException.class)
    ResponseEntity<?> execution(ExecutionException e) { return ResponseEntity.status(e.status).body(Map.of("error_code", "EXECUTION_ERROR", "message", e.getMessage())); }
    @ExceptionHandler(CommitmentException.class)
    ResponseEntity<?> commitment(CommitmentException e) { return ResponseEntity.status(e.status()).body(Map.of("error_code", e.code(), "message", e.getMessage())); }
    @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,
        org.springframework.web.bind.MissingRequestHeaderException.class,
        org.springframework.http.converter.HttpMessageNotReadableException.class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    ResponseEntity<?> invalid(Exception e) { return ResponseEntity.badRequest().body(Map.of("error_code", "VALIDATION_ERROR", "message", "Provide a valid work window, completion report and Idempotency-Key.")); }
}
