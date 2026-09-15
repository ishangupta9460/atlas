package com.atlas.backend.category;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes=CategoryController.class)
public class CategoryImportanceExceptionHandler {
    @ExceptionHandler(CategoryImportanceException.class)
    ResponseEntity<Map<String,String>> invalid(CategoryImportanceException ex) {
        return ResponseEntity.badRequest().body(Map.of("error_code","VALIDATION_ERROR","message",ex.getMessage()));
    }
}
