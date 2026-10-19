package com.gymkhata.common.error;

import com.gymkhata.common.audit.AuditService;
import com.gymkhata.domain.PhoneUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final AuditService auditService;

    public GlobalExceptionHandler(AuditService auditService) {
        this.auditService = auditService;
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(ApiError.of(ex.getStatus(), ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> details = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            details.put(fe.getField(), fe.getDefaultMessage());
        }
        return ResponseEntity.badRequest()
                .body(ApiError.of(400, "VALIDATION_FAILED", "Validation failed", details));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiError> handleDuplicateKey(DuplicateKeyException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, "DUPLICATE", ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of(403, "FORBIDDEN", "Access denied"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleOther(Exception ex) {
        log.error("Unhandled error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(500, "INTERNAL_ERROR", "Something went wrong"));
    }

    private static final java.util.regex.Pattern PHONE_PATTERN =
            java.util.regex.Pattern.compile("\\+91[6-9]\\d{9}");

    /** Masks any phone-like strings before they reach the audit log. */
    public static String maskPhones(String detail) {
        if (detail == null) {
            return null;
        }
        return PHONE_PATTERN.matcher(detail).replaceAll(m -> PhoneUtils.mask(m.group()));
    }
}
