package com.example.gateway.application.dto.attestation;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AttestationErrorResponse(
    String error,
    String message,
    Object details
) {
    
    public static AttestationErrorResponse badRequest(String message) {
        return new AttestationErrorResponse("Bad Request", message, null);
    }
    
    public static AttestationErrorResponse notFound(String message) {
        return new AttestationErrorResponse("Not Found", message, null);
    }
    
    public static AttestationErrorResponse internalError(String message) {
        return new AttestationErrorResponse("Internal Server Error", message, null);
    }
    
    public static AttestationErrorResponse validationError(String message, Object details) {
        return new AttestationErrorResponse("Validation Error", message, details);
    }
}