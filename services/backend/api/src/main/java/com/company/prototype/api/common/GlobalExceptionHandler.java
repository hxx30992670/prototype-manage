package com.company.prototype.api.common;

import com.company.prototype.common.error.ApiErrorCode;
import com.company.prototype.common.error.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApiException(ApiException e) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", e.getErrorCode().name());
        body.put("message", e.getMessage());
        body.put("traceId", UUID.randomUUID().toString().replace("-", ""));
        body.put("fieldErrors", List.of());
        return ResponseEntity.status(e.getErrorCode().getHttpStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException e) {
        List<Map<String, String>> fieldErrors = e.getBindingResult().getFieldErrors().stream()
            .map(fe -> Map.of("field", fe.getField(), "message", fe.getDefaultMessage() != null ? fe.getDefaultMessage() : ""))
            .collect(Collectors.toList());

        Map<String, Object> body = new HashMap<>();
        body.put("code", ApiErrorCode.VALIDATION_FAILED.name());
        body.put("message", ApiErrorCode.VALIDATION_FAILED.getDefaultMessage());
        body.put("traceId", UUID.randomUUID().toString().replace("-", ""));
        body.put("fieldErrors", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneralException(Exception e) {
        log.error("Unhandled exception", e);
        Map<String, Object> body = new HashMap<>();
        body.put("code", ApiErrorCode.INTERNAL_SERVER_ERROR.name());
        body.put("message", ApiErrorCode.INTERNAL_SERVER_ERROR.getDefaultMessage());
        body.put("traceId", UUID.randomUUID().toString().replace("-", ""));
        body.put("fieldErrors", List.of());
        return ResponseEntity.status(500).body(body);
    }
}
