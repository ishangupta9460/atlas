package com.atlas.backend;

import com.atlas.backend.auth.EmailAlreadyTakenException;
import com.atlas.backend.auth.InvalidCredentialsException;
import com.atlas.backend.task.InvalidTaskStateException;
import com.atlas.backend.task.TaskNotFoundException;
import com.atlas.backend.goal.GoalNotFoundException;
import com.atlas.backend.goal.InvalidGoalStateException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler providing standardized JSON error responses.
 *
 * Error shape matches 12_API_SPECIFICATION.md §1:
 * {
 *   "error_code": "ERROR_CODE",
 *   "message": "Human readable summary",
 *   "details": [ ... optional list of detailed errors ... ]
 * }
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getAllErrors().stream()
                .map(error -> {
                    if (error instanceof FieldError fieldError) {
                        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
                    }
                    return error.getDefaultMessage();
                })
                .collect(Collectors.toList());

        Map<String, Object> body = new HashMap<>();
        body.put("error_code", "VALIDATION_ERROR");
        body.put("message", "Validation failed for request arguments");
        body.put("details", details);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(EmailAlreadyTakenException.class)
    public ResponseEntity<Map<String, Object>> handleEmailAlreadyTaken(EmailAlreadyTakenException ex) {
        Map<String, Object> body = Map.of(
                "error_code", "EMAIL_TAKEN",
                "message", ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidCredentials(InvalidCredentialsException ex) {
        Map<String, Object> body = Map.of(
                "error_code", "INVALID_CREDENTIALS",
                "message", ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(TaskNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleTaskNotFound(TaskNotFoundException ex) {
        Map<String, Object> body = Map.of(
                "error_code", "TASK_NOT_FOUND",
                "message", ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(InvalidTaskStateException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidTaskState(InvalidTaskStateException ex) {
        Map<String, Object> body = Map.of(
                "error_code", "INVALID_TASK_STATE",
                "message", ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(GoalNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleGoalNotFound(GoalNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error_code", "GOAL_NOT_FOUND", "message", ex.getMessage()));
    }

    @ExceptionHandler(InvalidGoalStateException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidGoalState(InvalidGoalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error_code", "INVALID_GOAL_STATE", "message", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unhandled exception caught in GlobalExceptionHandler", ex);
        Map<String, Object> body = Map.of(
                "error_code", "INTERNAL_SERVER_ERROR",
                "message", "An unexpected error occurred"
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
