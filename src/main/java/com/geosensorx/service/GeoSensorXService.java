package com.geosensorx.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.geosensorx.data.DeviceEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.async.DeferredResult;


public interface GeoSensorXService {

    DeferredResult<ResponseEntity> processDeviceRestApiRequestToRuleEngine(String jwtToken, String deviceName, JsonNode requestBody, long restApiTimeout);

    DeferredResult<ResponseEntity> processOwnerRestApiRequestToRuleEngine(String jwtToken, JsonNode requestBody, long restApiTimeout);

    DeferredResult<ResponseEntity> processLogin(JsonNode loginRequest);

    DeferredResult<ResponseEntity> processRefreshToken(JsonNode refreshToken);

    DeferredResult<ResponseEntity> processSaveDevice(String jwtToken, DeviceEntity deviceEntity);

    DeferredResult<ResponseEntity> processGetDeviceByName(String jwtToken, String deviceName);

    DeferredResult<ResponseEntity> processGetDeviceIdByName(String jwtToken, String deviceName);

    DeferredResult<ResponseEntity> processGetPresignedUrlDownloadExtractedMedia(String deviceName, String deviceToken, String fileName);

    DeferredResult<ResponseEntity> processGetRpcById(String jwtToken, String rpcId, long restApiTimeout);

    DeferredResult<ResponseEntity> processGetAttributes(String jwtToken, String deviceId);

    DeferredResult<ResponseEntity> processSetAttributes(String jwtToken, String deviceId, JsonNode requestBody);

    DeferredResult<ResponseEntity> processCreateLiveStream(String jwtToken, String deviceName, JsonNode requestBody, long restApiTimeout);

    DeferredResult<ResponseEntity> processDeleteLiveStream(String jwtToken, String deviceName, long restApiTimeout);

    DeferredResult<ResponseEntity> processGetLiveStreams(String jwtToken, JsonNode requestBody);

}
