package com.atlas.backend.event;

import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = EventController.class)
public class EventQueryExceptionHandler {
    @ExceptionHandler(EventQueryService.EntityNotFound.class)
    ResponseEntity<?> missing() {
        return ResponseEntity.status(404).body(Map.of("error_code", "ENTITY_NOT_FOUND", "message", "Entity not found"));
    }
    @ExceptionHandler({IllegalArgumentException.class, MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class})
    ResponseEntity<?> invalid(Exception ignored) {
        return ResponseEntity.badRequest().body(Map.of("error_code", "VALIDATION_ERROR", "message", "Invalid event query"));
    }
}
