package com.geosensorx.exception;

import org.springframework.http.HttpStatus;

import java.util.Date;

public class GeoSensorXErrorResponse {

    // HTTP Response Status Code
    private final HttpStatus status;

    // General Error message
    private final String message;

    // Error code
    private final GeoSensorXErrorCode errorCode;

    private final Date timestamp;

    public GeoSensorXErrorResponse(final String message, final GeoSensorXErrorCode errorCode, HttpStatus status) {
        this.message = message;
        this.errorCode = errorCode;
        this.status = status;
        this.timestamp = new java.util.Date();
    }

    public static GeoSensorXErrorResponse of(final String message, final GeoSensorXErrorCode errorCode, HttpStatus status) {
        return new GeoSensorXErrorResponse(message, errorCode, status);
    }

    public Integer getStatus() {
        return status.value();
    }

    public String getMessage() {
        return message;
    }

    public GeoSensorXErrorCode getErrorCode() {
        return errorCode;
    }

    public Date getTimestamp() {
        return timestamp;
    }

}
