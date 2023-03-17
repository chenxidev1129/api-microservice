package com.geosensorx.exception;

import com.fasterxml.jackson.annotation.JsonValue;

public enum GeoSensorXErrorCode {

    GENERAL(1),
    INVALID_ARGUMENTS(2),
    BAD_REQUEST_PARAMS(3),
    ITEM_NOT_FOUND(4),
    UNAUTHORIZED(5);

    private int errorCode;

    GeoSensorXErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }

    @JsonValue
    public int getErrorCode() {
        return errorCode;
    }

}
