package com.geosensorx.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
@Slf4j
public class GeoSensorXErrorResponseHandler {

    @Autowired
    private ObjectMapper mapper;


    public void handle(Exception exception, HttpServletResponse response) {
        log.debug("Processing exception {}", exception.getMessage(), exception);
        if (!response.isCommitted()) {
            try {
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                if (exception instanceof GeoSensorXException) {
                    handleGeoSensorXException((GeoSensorXException) exception, response);
                } else {
                    response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
                    mapper.writeValue(response.getWriter(), GeoSensorXErrorResponse.of(exception.getMessage(),
                            GeoSensorXErrorCode.GENERAL, HttpStatus.INTERNAL_SERVER_ERROR));
                }
            } catch (IOException e) {
                log.error("Can't handle exception", e);
            }
        }
    }


    private void handleGeoSensorXException(GeoSensorXException geoSensorXException, HttpServletResponse response) throws IOException {

        GeoSensorXErrorCode errorCode = geoSensorXException.getErrorCode();
        HttpStatus status;

        switch (errorCode) {
            case INVALID_ARGUMENTS:
            case BAD_REQUEST_PARAMS:
                status = HttpStatus.BAD_REQUEST;
                break;
            case ITEM_NOT_FOUND:
                status = HttpStatus.NOT_FOUND;
                break;
            case UNAUTHORIZED:
                status = HttpStatus.UNAUTHORIZED;
                break;
            default:
                status = HttpStatus.INTERNAL_SERVER_ERROR;
                break;
        }

        response.setStatus(status.value());
        mapper.writeValue(response.getWriter(), GeoSensorXErrorResponse.of(geoSensorXException.getMessage(), errorCode, status));
    }

}