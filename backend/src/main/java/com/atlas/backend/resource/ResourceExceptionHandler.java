package com.atlas.backend.resource;

import com.atlas.backend.execution.ExecutionException;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages={"com.atlas.backend.resource","com.atlas.backend.ingestion"})
public class ResourceExceptionHandler {
    @ExceptionHandler(ExecutionException.class) ResponseEntity<?> state(ExecutionException e){return ResponseEntity.status(e.status()).body(Map.of("error_code","IMPORT_RESOURCE_ERROR","message",e.getMessage()));}
    @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<?> invalid(IllegalArgumentException e){return ResponseEntity.badRequest().body(Map.of("error_code","VALIDATION_ERROR","message",e.getMessage()));}
    @ExceptionHandler(com.atlas.backend.fixedcommitment.InvalidFixedCommitmentException.class)
    ResponseEntity<?> fixed(com.atlas.backend.fixedcommitment.InvalidFixedCommitmentException e){return ResponseEntity.badRequest().body(Map.of("error_code","VALIDATION_ERROR","message",e.getMessage()));}
    @ExceptionHandler(com.atlas.backend.commitment.CommitmentException.class)
    ResponseEntity<?> commitment(com.atlas.backend.commitment.CommitmentException e){return ResponseEntity.status(e.status()).body(Map.of("error_code",e.code(),"message",e.getMessage()));}
    @ExceptionHandler({org.springframework.web.bind.MissingRequestHeaderException.class,org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,org.springframework.web.multipart.support.MissingServletRequestPartException.class})
    ResponseEntity<?> malformed(Exception e){return ResponseEntity.badRequest().body(Map.of("error_code","VALIDATION_ERROR","message","Provide valid input and an Idempotency-Key."));}
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    ResponseEntity<?> oversized(Exception e){return ResponseEntity.status(413).body(Map.of("error_code","UPLOAD_TOO_LARGE","message","Upload exceeds the permitted size."));}
}
