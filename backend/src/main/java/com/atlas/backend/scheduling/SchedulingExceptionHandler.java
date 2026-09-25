package com.atlas.backend.scheduling;

import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.atlas.backend.scheduling")
public class SchedulingExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<?> invalid(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("error_code", "VALIDATION_ERROR", "message", exception.getMessage()));
    }

    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class})
    ResponseEntity<?> malformed(Exception exception) {
        return ResponseEntity.badRequest().body(Map.of("error_code", "VALIDATION_ERROR",
                "message", "Provide valid scheduling configuration and explicit-offset planning times."));
    }
}
