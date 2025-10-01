package com.example.gateway.application.dto.exception;

/**
 * Exception thrown when ServiceNow (source of truth) data is missing, null, or invalid.
 * This indicates a serious data quality issue that requires immediate attention.
 * 
 * Unlike validation exceptions, this should fail hard (500 error) since it represents
 * a system-level data integrity problem, not a client input error.
 */
public class DataIntegrityException extends RuntimeException {

    private final String dataSource;
    private final String appId;
    private final String fieldName;
    private final Object invalidValue;

    public DataIntegrityException(String dataSource, String appId, String fieldName, Object invalidValue, String message) {
        super(String.format("[%s] Data integrity violation for appId=%s, field=%s: %s (value: '%s')", 
                dataSource, appId, fieldName, message, invalidValue));
        this.dataSource = dataSource;
        this.appId = appId;
        this.fieldName = fieldName;
        this.invalidValue = invalidValue;
    }

    public DataIntegrityException(String dataSource, String appId, String fieldName, Object invalidValue, String message, Throwable cause) {
        super(String.format("[%s] Data integrity violation for appId=%s, field=%s: %s (value: '%s')", 
                dataSource, appId, fieldName, message, invalidValue), cause);
        this.dataSource = dataSource;
        this.appId = appId;
        this.fieldName = fieldName;
        this.invalidValue = invalidValue;
    }

    public String getDataSource() {
        return dataSource;
    }

    public String getAppId() {
        return appId;
    }

    public String getFieldName() {
        return fieldName;
    }

    public Object getInvalidValue() {
        return invalidValue;
    }
}