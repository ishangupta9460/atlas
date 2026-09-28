package com.atlas.backend.recovery;

import com.atlas.backend.execution.ExecutionException;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages="com.atlas.backend.recovery")
public class RecoveryExceptionHandler {
    @ExceptionHandler(ExecutionException.class)
    ResponseEntity<?> execution(ExecutionException e) {
        return ResponseEntity.status(e.status()).body(Map.of("error_code","RECOVERY_ERROR","message",e.getMessage()));
    }
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<?> invalid(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error_code","VALIDATION_ERROR","message",e.getMessage()));
    }
    @ExceptionHandler({org.springframework.web.bind.MissingRequestHeaderException.class,
        org.springframework.http.converter.HttpMessageNotReadableException.class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    ResponseEntity<?> malformed(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("error_code","VALIDATION_ERROR","message","Provide valid recovery input and an Idempotency-Key."));
    }
}
