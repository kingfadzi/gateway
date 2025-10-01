package com.example.onboarding.web;

import com.example.onboarding.application.dto.exception.DataIntegrityException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.NoSuchElementException;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<?> notFound(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem(404, "Not Found", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<?> conflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem(409, "Conflict", ex.getMessage()));
    }

    @ExceptionHandler(DataIntegrityException.class)
    public ResponseEntity<?> dataIntegrityFailure(DataIntegrityException ex) {
        // Data integrity issues from source systems should fail hard (500)
        // This indicates a serious system-level problem requiring immediate attention
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(dataIntegrityProblem(ex));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem(422, "Validation error", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> generic(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(problem(500, "Internal Server Error", ex.getMessage()));
    }

    private Map<String,Object> problem(int status, String title, String detail) {
        return Map.of("type", "about:blank", "status", status, "title", title, "detail", detail);
    }

    private Map<String,Object> dataIntegrityProblem(DataIntegrityException ex) {
        return Map.of(
                "type", "about:blank", 
                "status", 500, 
                "title", "Data Integrity Failure",
                "detail", ex.getMessage(),
                "dataSource", ex.getDataSource(),
                "appId", ex.getAppId(),
                "fieldName", ex.getFieldName(),
                "invalidValue", ex.getInvalidValue() != null ? ex.getInvalidValue().toString() : "null"
        );
    }
}
