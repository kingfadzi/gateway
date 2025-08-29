package com.example.onboarding.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/**
 * Flexible deserializer for OffsetDateTime that handles both:
 * 1. Complete ISO datetime with timezone: "2025-08-29T15:03:00Z"
 * 2. Partial datetime without timezone: "2025-08-29T15:03" (defaults to UTC)
 * 
 * This provides backwards compatibility while encouraging proper timezone usage.
 */
public class FlexibleOffsetDateTimeDeserializer extends JsonDeserializer<OffsetDateTime> {
    
    private static final Logger log = LoggerFactory.getLogger(FlexibleOffsetDateTimeDeserializer.class);
    
    @Override
    public OffsetDateTime deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
        String dateStr = p.getValueAsString();
        
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        
        dateStr = dateStr.trim();
        
        try {
            // Try parsing as complete OffsetDateTime first (preferred)
            return OffsetDateTime.parse(dateStr);
        } catch (DateTimeParseException e) {
            try {
                // Fall back to LocalDateTime and assume UTC
                LocalDateTime localDateTime = LocalDateTime.parse(dateStr);
                log.debug("Parsed datetime without timezone '{}', defaulting to UTC", dateStr);
                return localDateTime.atOffset(ZoneOffset.UTC);
            } catch (DateTimeParseException ex) {
                // Try with seconds if missing
                try {
                    LocalDateTime localDateTime = LocalDateTime.parse(dateStr + ":00");
                    log.debug("Parsed datetime without seconds '{}', defaulting to UTC", dateStr);
                    return localDateTime.atOffset(ZoneOffset.UTC);
                } catch (DateTimeParseException finalEx) {
                    throw new IllegalArgumentException(
                        "Invalid datetime format: '" + dateStr + "'. " +
                        "Expected formats: '2025-08-29T15:03:00Z' or '2025-08-29T15:03'", 
                        finalEx
                    );
                }
            }
        }
    }
}