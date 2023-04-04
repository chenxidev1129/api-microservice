package com.geosensorx.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.geosensorx.data.DeviceEntity;
import com.geosensorx.exception.GeoSensorXErrorCode;
import com.geosensorx.exception.GeoSensorXErrorResponseHandler;
import com.geosensorx.exception.GeoSensorXException;
import com.geosensorx.service.GeoSensorXService;
import org.apache.coyote.Request;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.DataConstants;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.kv.DataType;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;

@RestController
@RequestMapping("/api/geosensorx")
public class GeoSensorXController {

    private static final ObjectMapper mapper = JacksonUtil.OBJECT_MAPPER;

    private static final String GET_DEVICE_LATEST_TELEMETRY_METHOD = "getDeviceLatestTelemetry";
    private static final String GET_DEVICE_TELEMETRY_METHOD = "getDeviceTelemetry";
    private static final String GET_DEVICE_REAL_TIME_LOCATION = "getDeviceRealTimeLocation";
    private static final String GET_DEVICE_TELEMETRY_BY_KEY_VALUE = "getDeviceTelemetryByKeyValue";

    private static final String GET_OWNER_DEVICE_ATTRIBUTES = "getOwnerDeviceAttributes";
    private static final String GET_OWNER_ATTRIBUTES = "getOwnerAttributes";
    private static final String GET_OWNER_DEVICE_NAMES = "getOwnerDeviceNames";
    private static final String SAVE_OWNER_ATTRIBUTES = "saveOwnerAttributes";
    private static final String RPC_METHOD = "RPC";
    private static final String MEDIA_REQUEST = "MEDIA_REQUEST";
    private static final String SAVE_DEVICE_CREDENTIALS = "SAVE_DEVICE_CREDENTIALS";
    private static final String SAVE_DEVICE_ATTRIBUTES = "SAVE_DEVICE_ATTRIBUTES";
    private static final String SAVE_OWNER_ATTRIBUTES_USING_DEVICE = "SAVE_OWNER_ATTRIBUTES_USING_DEVICE";
    private static final String POST_TELEMETRY = "POST_TELEMETRY";
    private static final String CONFIG_SET = "CONFIG_SET";
    private static final String CONFIG_GET = "CONFIG_GET";
    public static final String GET_COMPLETED_RPC_DATA_METHOD = "GET_COMPLETED_RPC_DATA";

    @Autowired
    private GeoSensorXErrorResponseHandler errorResponseHandler;

    @Autowired
    public GeoSensorXService geoSensorXService;

    @ExceptionHandler(GeoSensorXException.class)
    public void handleGeoSensorXException(GeoSensorXException ex, HttpServletResponse response) {
        errorResponseHandler.handle(ex, response);
    }

    @RequestMapping(value = "/auth/login", method = RequestMethod.GET)
    @ResponseBody
    public DeferredResult<ResponseEntity> getJwtToken(@RequestParam("username") String username,
                                                      @RequestParam(name = "password") String password,
                                                      HttpServletRequest httpServletRequest) throws GeoSensorXException {

        ObjectNode loginRequest = mapper.createObjectNode();
        loginRequest.put("username", username);
        loginRequest.put("password", password);

        try {
            return geoSensorXService.processLogin(loginRequest);
        } catch (Exception e) {
            throw new GeoSensorXException("Failed to process request due to: " + e + "!", GeoSensorXErrorCode.UNAUTHORIZED);
        }
    }

    @RequestMapping(value = "/auth/token", method = RequestMethod.GET)
    @ResponseBody
    public DeferredResult<ResponseEntity> refreshToken(@RequestParam("refreshToken") String refreshToken,
                                                       HttpServletRequest httpServletRequest) throws GeoSensorXException {

        if (StringUtils.isEmpty(refreshToken)) {
            throw new GeoSensorXException("Invalid refreshToken: " + refreshToken + "!", GeoSensorXErrorCode.INVALID_ARGUMENTS);
        }

        ObjectNode refreshTokenRequest = mapper.createObjectNode();
        refreshTokenRequest.put("refreshToken", refreshToken);

        try {
            return geoSensorXService.processRefreshToken(refreshTokenRequest);
        } catch (Exception e) {
            throw new GeoSensorXException("Failed to process request due to: " + e + "!", GeoSensorXErrorCode.GENERAL);
        }
    }

    @RequestMapping(value = "/device/{deviceName}", method = RequestMethod.GET)
    @ResponseBody
    public DeferredResult<ResponseEntity> getDeviceIdByName(@PathVariable("deviceName") String deviceName,
                                                            HttpServletRequest httpServletRequest) throws GeoSensorXException {

        String jwtToken = getJwtTokenFromRequest(httpServletRequest);

        try {
            return geoSensorXService.processGetDeviceIdByName(jwtToken, deviceName);
        } catch (Exception e) {
            throw new GeoSensorXException("Failed to process request due to: " + e + "!", GeoSensorXErrorCode.GENERAL);
        }
    }

    @RequestMapping(value = "/{deviceName}/{deviceToken}/{fileName}/url", method = RequestMethod.GET)
    @ResponseBody
    public DeferredResult<ResponseEntity> getPresignedUrlDownloadExtractedMedia(@PathVariable("deviceName") String deviceName,
                                                                                @PathVariable("deviceToken") String deviceToken,
                                                                                @PathVariable("fileName") String fileName,
                                                                                HttpServletRequest httpServletRequest) throws GeoSensorXException {

        try {
            return geoSensorXService.processGetPresignedUrlDownloadExtractedMedia(deviceName, deviceToken, fileName);
        } catch (Exception e) {
            throw new GeoSensorXException("Failed to process request due to: " + e + "!", GeoSensorXErrorCode.GENERAL);
        }
    }

    @RequestMapping(value = "/{deviceName}/values/timeseries", method = RequestMethod.GET)
    @ResponseBody
    public DeferredResult<ResponseEntity> getDeviceLatestTelemetry(@PathVariable("deviceName") String deviceName,
                                                                   @RequestParam(name = "keys", required = false) String keysStr,
                                                                   @RequestParam(name = "fetchAllLatestTimeseries", required = false, defaultValue = "false") boolean fetchAllLatestTimeseries,
                                                                   @RequestParam(name = "restApiTimeout", required = false, defaultValue = "15000") long restApiTimeout,
                                                                   HttpServletRequest httpServletRequest) throws GeoSensorXException {

        String jwtToken = getJwtTokenFromRequest(httpServletRequest);

        ObjectNode request = mapper.createObjectNode();
        request.put("method", GET_DEVICE_LATEST_TELEMETRY_METHOD);
        ObjectNode params = mapper.createObjectNode();
        params.put("keys", keysStr == null ? "" : keysStr);
        params.put("fetchAllLatestTimeseries", fetchAllLatestTimeseries);
        params.put("deviceName", deviceName);
        request.set("params", params);

        try {
            return geoSensorXService.processDeviceRestApiRequestToRuleEngine(jwtToken, deviceName, request, restApiTimeout);
        } catch (Exception e) {
            throw new GeoSensorXException("Failed to process request due to: " + e + "!", GeoSensorXErrorCode.GENERAL);
        }
    }

    @RequestMapping(value = "/{deviceName}/values/timeseries", method = RequestMethod.GET, params = {"fetchAllTimeseries", "start", "end", "limit"})
    @ResponseBody
    public DeferredResult<ResponseEntity> getDeviceTelemetryInRange(@PathVariable("deviceName") String deviceName,
                                                                    @RequestParam(name = "keys", required = false) String keysStr,
                                                                    @RequestParam(name = "fetchAllTimeseries", required = false, defaultValue = "false") boolean fetchAllTimeseries,
                                                                    @RequestParam(name = "start") String start,
                                                                    @RequestParam(name = "end") String end,
                                                                    @RequestParam(name = "limit", defaultValue = "100") Integer limit,
                                                                    @RequestParam(name = "restApiTimeout", required = false, defaultValue = "15000") long restApiTimeout,
                                                                    HttpServletRequest httpServletRequest) throws GeoSensorXException {

        String jwtToken = getJwtTokenFromRequest(httpServletRequest);

        ObjectNode request = mapper.createObjectNode();
        request.put("method", GET_DEVICE_TELEMETRY_METHOD);
        ObjectNode params = mapper.createObjectNode();

        putDateToParams(start, params, "startTs");
        putDateToParams(end, params, "endTs");

        params.put("keys", keysStr == null ? "" : keysStr);
        params.put("fetchAllTimeseries", fetchAllTimeseries);
        params.put("limit", limit);
        params.put("deviceName", deviceName);
        request.set("params", params);

        try {
            return geoSensorXService.processDeviceRestApiRequestToRuleEngine(jwtToken, deviceName, request, restApiTimeout);
        } catch (Exception e) {
            throw new GeoSensorXException("Failed to process request due to: " + e + "!", GeoSensorXErrorCode.GENERAL);
        }
    }

    @RequestMapping(value = "/{deviceName}/values/timeseries", method = RequestMethod.GET, params = {"key", "value", "dataType"})
    @ResponseBody
    public DeferredResult<ResponseEntity> getDeviceTelemetryByKeyValue(@PathVariable("deviceName") String deviceName,
                                                                       @RequestParam(name = "key") String key,
                                                                       @RequestParam(name = "value") String value,
                                                                       @RequestParam(name = "dataType") DataType dataType,
                                                                       @RequestParam(name = "start", required = false) String start,
                                                                       @RequestParam(name = "end", required = false) String end,
                                                                       @RequestParam(name = "restApiTimeout", required = false, defaultValue = "15000") long restApiTimeout,
                                                                       HttpServletRequest httpServletRequest) throws GeoSensorXException {

        String jwtToken = getJwtTokenFromRequest(httpServletRequest);

        ObjectNode request = mapper.createObjectNode();
        request.put("method", GET_DEVICE_TELEMETRY_BY_KEY_VALUE);
        ObjectNode params = mapper.createObjectNode();

        if (!StringUtils.isEmpty(start) && !StringUtils.isEmpty(end)) {
            putDateToParams(start, params, "startTs");
            putDateToParams(end, params, "endTs");
        }

        params.put("key", key);
        params.put("value", value);
        params.put("dataType", dataType.name());

        params.put("deviceName", deviceName);
        request.set("params", params);

        try {
            return geoSensorXService.processDeviceRestApiRequestToRuleEngine(jwtToken, deviceName, request, restApiTimeout);
        } catch (Exception e) {
            throw new GeoSensorXException("Failed to process request due to: " + e + "!", GeoSensorXErrorCode.GENERAL);
        }
    }

    @RequestMapping(value = "/rpc/{deviceName}", method = RequestMethod.POST)
    @ResponseBody
    public DeferredResult<ResponseEntity> processRpcRequest(@PathVariable("deviceName") String deviceName,
                                                            @RequestParam(name = "retries", required = false, defaultValue = "0") int retries,
                                                            @RequestParam(name = "rpcExpirationTime", required = false, defaultValue = "126227808000") long rpcExpirationTime,
                                                            @RequestParam(name = "restApiTimeout", required = false, defaultValue = "15000") long restApiTimeout,
                                                            @RequestBody String rpcRequest,
                                                            HttpServletRequest httpServletRequest) throws GeoSensorXException {
        JsonNode request;
        try {
            request = mapper.readTree(rpcRequest);
        } catch (JsonProcessingException e) {
            throw new GeoSensorXException("Failed to process request due to: " + e + "!", GeoSensorXErrorCode.GENERAL);
        }
        return processRpc(deviceName, request, retries, rpcExpirationTime, restApiTimeout, httpServletRequest);
    }

    @RequestMapping(value = "/{deviceName}/values/realtimeLocation", method = RequestMethod.GET)
    @ResponseBody
    public DeferredResult<ResponseEntity> getRealtimeLocation(@PathVariable("deviceName") String deviceName,
                                                              @RequestParam(name = "keys", required = false) String keysStr,
                                                              @RequestParam(name = "timeout", defaultValue = "10000") long timeout,
                                                              @RequestParam(name = "fetchAllTimeseries", required = false, defaultValue = "false") boolean fetchAllTimeseries,
                                                              @RequestParam(name = "retries", required = false, defaultValue = "0") int retries,
                                                              @RequestParam(name = "rpcExpirationTime", required = false, defaultValue = "126227808000") long rpcExpirationTime,
                                                              @RequestParam(name = "restApiTimeout", required = false, defaultValue = "15000") long restApiTimeout,
                                                              HttpServletRequest httpServletRequest) throws GeoSensorXException {
        ObjectNode rpcRequest = mapper.createObjectNode();
        rpcRequest.put("method", "getgps");
        rpcRequest.put("params", "?");
        rpcRequest.put("keys", keysStr == null ? "" : keysStr);
        rpcRequest.put("fetchAllTimeseries", fetchAllTimeseries);
        rpcRequest.put("timeout", timeout);
        return processRpc(deviceName, rpcRequest, retries, rpcExpirationTime, restApiTimeout, httpServletRequest);
    }

    @RequestMapping(value = "/media", method = RequestMethod.POST)
    @ResponseBody
    public DeferredResult<ResponseEntity> processMediaRequest(@RequestBody String mediaRequest,
                                                              @RequestParam(name = "retries", required = false, defaultValue = "2") int retries,
                                                              @RequestParam(name = "rpcExpirationTime", required = false, defaultValue = "126227808000") long rpcExpirationTime,
                                                              @RequestParam(name = "restApiTimeout", required = false, defaultValue = "15000") long restApiTimeout,
                                                              HttpServletRequest httpServletRequest) throws GeoSensorXException {
        String jwtToken = getJwtTokenFromRequest(httpServletRequest);
        ObjectNode mediaRequestNode;
        try {
            mediaRequestNode = (ObjectNode) mapper.readTree(mediaRequest);
        } catch (JsonProcessingException e) {
            throw new GeoSensorXException("Failed to process request due to: " + e + "!", GeoSensorXErrorCode.GENERAL);
        }
        validateMediaRpcRequest(mediaRequestNode);
        mediaRequestNode.put("retries", retries);
        mediaRequestNode.put("expirationTime", System.currentTimeMillis() + rpcExpirationTime);
        String deviceName = mediaRequestNode.get("deviceName").asText();

        ObjectNode mediaRpcRequest = mapper.createObjectNode();
        mediaRpcRequest.put("method", MEDIA_REQUEST);
        mediaRpcRequest.set("params", mediaRequestNode);
        return geoSensorXService.processDeviceRestApiRequestToRuleEngine(jwtToken, deviceName, mediaRpcRequest, restApiTimeout);
    }

    @RequestMapping(value = "/rpc/{rpcId}", method = RequestMethod.GET)
    @ResponseBody
    public DeferredResult<ResponseEntity> getRpcById(@PathVariable("rpcId") String rpcId,
                                                     @RequestParam(name = "restApiTimeout", required = false, defaultValue = "15000") long restApiTimeout,
                                                     HttpServletRequest httpServletRequest) throws GeoSensorXException {
        String jwtToken = getJwtTokenFromRequest(httpServletRequest);
        return geoSensorXService.processGetRpcById(jwtToken, rpcId, restApiTimeout);
    }

    private void validateMediaRpcRequest(JsonNode mediaRequestNode) throws GeoSensorXException {
        if (mediaRequestNode == null || mediaRequestNode.isNull() || mediaRequestNode.isEmpty()) {
            throw new GeoSensorXException("Failed to process request due to empty mediaRequest!", GeoSensorXErrorCode.BAD_REQUEST_PARAMS);
        }
        if (mediaRequestNode.hasNonNull("eventTime") || mediaRequestNode.hasNonNull("fileName")) {
            if (!mediaRequestNode.hasNonNull("deviceName")) {
                throw new GeoSensorXException("Failed to process media request due to empty or null 'deviceName' parameter!", GeoSensorXErrorCode.BAD_REQUEST_PARAMS);
            }
        } else {
            throw new GeoSensorXException("Failed to process media request due to empty or null value for one of the next parameters: 'eventTime' or 'fileName'! " +
                    "At least one of these parameters should be present in the request!", GeoSensorXErrorCode.BAD_REQUEST_PARAMS);
        }
    }

    private DeferredResult<ResponseEntity> processRpc(String deviceName, JsonNode rpcRequest, int retries, long rpcExpirationTime, long restApiTimeout, HttpServletRequest httpServletRequest) throws GeoSensorXException {
        String jwtToken = getJwtTokenFromRequest(httpServletRequest);

        if (rpcRequest == null || rpcRequest.isNull() || rpcRequest.isEmpty()) {
            throw new GeoSensorXException("Invalid RPC request body structure: " + rpcRequest + "!", GeoSensorXErrorCode.BAD_REQUEST_PARAMS);
        }
        if (!rpcRequest.hasNonNull("method")) {
            throw new GeoSensorXException("Invalid RPC request body structure: " + rpcRequest + ". Reason: Method is not present in the RPC request body!", GeoSensorXErrorCode.BAD_REQUEST_PARAMS);
        }
        if (!rpcRequest.hasNonNull("params")) {
            throw new GeoSensorXException("Invalid RPC request body structure: " + rpcRequest + ". Reason: Params are not present in the RPC request body!", GeoSensorXErrorCode.BAD_REQUEST_PARAMS);
        }

        ObjectNode request = mapper.createObjectNode();
        request.put("method", RPC_METHOD);
        ObjectNode rpcRequestNode = mapper.createObjectNode();
        rpcRequestNode.put("method", rpcRequest.get("method").asText());
        JsonNode rpcParamsNode = rpcRequest.get("params");
        if (rpcParamsNode.isTextual()) {
            String params = rpcParamsNode.toString();
            rpcRequestNode.put("params", params);
        } else {
            rpcRequestNode.set("params", rpcParamsNode);
        }
        ObjectNode params = mapper.createObjectNode();
        params.put("retries", retries);
        if (rpcExpirationTime > 0) {
            params.put("expirationTime", System.currentTimeMillis() + rpcExpirationTime);
        }
        params.set("rpcRequest", rpcRequestNode);
        params.put("reportCompletedStatus",
                rpcRequest.hasNonNull("reportCompletedStatus") && rpcRequest.get("reportCompletedStatus").asBoolean(false));
        if (rpcRequest.hasNonNull("keys")) {
            ObjectNode getTimeseriesNode = mapper.createObjectNode();
            getTimeseriesNode.put("method", GET_DEVICE_REAL_TIME_LOCATION);
            ObjectNode getTimeseriesParams = mapper.createObjectNode();
            getTimeseriesParams.put("keys", rpcRequest.get("keys").asText());
            if (rpcRequest.hasNonNull("fetchAllTimeseries")) {
                getTimeseriesParams.put("fetchAllTimeseries", rpcRequest.get("fetchAllTimeseries").booleanValue());
            } else {
                getTimeseriesParams.put("fetchAllTimeseries", false);
            }
            getTimeseriesNode.set("params", getTimeseriesParams);
            params.set("getTimeseriesRequest", getTimeseriesNode);
            params.put("deviceName", deviceName);
        }
        request.set("params", params);
        try {
            return geoSensorXService.processDeviceRestApiRequestToRuleEngine(jwtToken, deviceName, request, restApiTimeout);
        } catch (Exception e) {
            throw new GeoSensorXException("Failed to process request due to: " + e + "!", GeoSensorXErrorCode.GENERAL);
        }
    }

    @RequestMapping(value = "/device", method = RequestMethod.POST)
    @ResponseBody
    public DeferredResult<ResponseEntity> saveDevice(@RequestParam(name = "name") String name,
                                                     @RequestParam(name = "type") String type,
                                                     @RequestParam(name = "newName", required = false) String newName,
                                                     @RequestParam(name = "label", required = false) String label,
                                                     @RequestParam(name = "accessToken", required = false) String accessToken,
                                                     HttpServletRequest httpServletRequest) throws GeoSensorXException {

        String jwtToken = getJwtTokenFromRequest(httpServletRequest);
        DeviceEntity deviceEntity = new DeviceEntity();
        deviceEntity.setName(name);
        deviceEntity.setType(type);
        deviceEntity.setLabel(label);
        deviceEntity.setNewName(newName);
        deviceEntity.setAccessToken(accessToken);
        try {
            return geoSensorXService.processSaveDevice(jwtToken, deviceEntity);
        } catch (Exception e) {
            throw new GeoSensorXException("Failed to process request due to: " + e + "!", GeoSensorXErrorCode.GENERAL);
        }
    }

    @RequestMapping(value = "/device/credentials", method = RequestMethod.POST)
    @ResponseBody
    public DeferredResult<ResponseEntity> saveDeviceCredentials(@RequestParam(name = "deviceName") String deviceName,
                                                                @RequestParam(name = "accessToken") String accessToken,
                                                                @RequestParam(name = "restApiTimeout", required = false, defaultValue = "15000") long restApiTimeout,
                                                                HttpServletRequest httpServletRequest) throws GeoSensorXException {

        String jwtToken = getJwtTokenFromRequest(httpServletRequest);

        ObjectNode requestSaveDeviceCredentials = mapper.createObjectNode();
        requestSaveDeviceCredentials.put("method", SAVE_DEVICE_CREDENTIALS);

        ObjectNode params = mapper.createObjectNode();
        params.put("deviceName", deviceName);
        params.put("accessToken", accessToken);
        requestSaveDeviceCredentials.set("params", params);

        return geoSensorXService.processDeviceRestApiRequestToRuleEngine(jwtToken, deviceName, requestSaveDeviceCredentials, restApiTimeout);
    }

    @RequestMapping(value = "/device/{deviceName}/attributes/{scope}", method = RequestMethod.POST)
    @ResponseBody
    public DeferredResult<ResponseEntity> saveDeviceAttributes(@PathVariable("deviceName") String deviceName,
                                                               @PathVariable("scope") String scope,
                                                               @RequestParam(name = "restApiTimeout", required = false, defaultValue = "15000") long restApiTimeout,
                                                               @RequestBody String requestBody,
                                                               HttpServletRequest httpServletRequest) throws GeoSensorXException {

        String jwtToken = getJwtTokenFromRequest(httpServletRequest);

        if (StringUtils.isEmpty(scope) || Arrays.stream(DataConstants.allScopes()).noneMatch(scope::equals)) {
            throw new GeoSensorXException("Invalid scope provided: " + scope + "! Only " + Arrays.toString(DataConstants.allScopes()) + " allowed!", GeoSensorXErrorCode.INVALID_ARGUMENTS);
        }

        JsonNode requestNode;
        try {
            requestNode = mapper.readTree(requestBody);
        } catch (IOException e) {
            throw new GeoSensorXException("Invalid request body structure: " + requestBody + "! Reason: " + e.getMessage(), GeoSensorXErrorCode.BAD_REQUEST_PARAMS);
        }

        ObjectNode requestSaveAttributes = mapper.createObjectNode();
        requestSaveAttributes.put("method", SAVE_DEVICE_ATTRIBUTES);

        ObjectNode params = mapper.createObjectNode();
        params.put("deviceName", deviceName);
        params.put("scope", scope);
        params.set("request", requestNode);
        requestSaveAttributes.set("params", params);

        return geoSensorXService.processDeviceRestApiRequestToRuleEngine(jwtToken, deviceName, requestSaveAttributes, restApiTimeout);
    }

    @RequestMapping(value = "/device/{deviceName}/attributes", method = RequestMethod.POST)
    @ResponseBody
    public DeferredResult<ResponseEntity> saveOwnerAttributesUsingDeviceId(@PathVariable("deviceName") String deviceName,
                                                                           @RequestParam(name = "restApiTimeout", required = false, defaultValue = "15000") long restApiTimeout,
                                                                           @RequestBody String requestBody,
                                                                           HttpServletRequest httpServletRequest) throws GeoSensorXException {

        String jwtToken = getJwtTokenFromRequest(httpServletRequest);

        JsonNode requestNode;
        try {
            requestNode = mapper.readTree(requestBody);
        } catch (IOException e) {
            throw new GeoSensorXException("Invalid request body structure: " + requestBody + "! Reason: " + e.getMessage(), GeoSensorXErrorCode.BAD_REQUEST_PARAMS);
        }

        ObjectNode requestSaveAttributes = mapper.createObjectNode();
        requestSaveAttributes.put("method", SAVE_OWNER_ATTRIBUTES_USING_DEVICE);

        ObjectNode params = mapper.createObjectNode();
        params.put("deviceName", deviceName);
        params.put("scope", DataConstants.SERVER_SCOPE);
        params.set("request", requestNode);
        requestSaveAttributes.set("params", params);

        return geoSensorXService.processDeviceRestApiRequestToRuleEngine(jwtToken, deviceName, requestSaveAttributes, restApiTimeout);
    }

    @RequestMapping(value = "/{deviceName}/values/attributes", method = RequestMethod.GET)
    @ResponseBody
    public DeferredResult<ResponseEntity> getOwnerDeviceAttributes(@PathVariable("deviceName") String deviceName,
                                                                   @RequestParam(name = "scope") String scope,
                                                                   @RequestParam(name = "keys", required = false) String keysStr,
                                                                   @RequestParam(name = "getAttributesFromCustomerIfAbsent", required = false, defaultValue = "false") boolean getAttributesFromCustomerIfAbsent,
                                                                   @RequestParam(name = "restApiTimeout", required = false, defaultValue = "15000") long restApiTimeout,
                                                                   HttpServletRequest httpServletRequest) throws GeoSensorXException {

        String jwtToken = getJwtTokenFromRequest(httpServletRequest);

        if (StringUtils.isEmpty(scope) || Arrays.stream(DataConstants.allScopes()).noneMatch(scope::equals)) {
            throw new GeoSensorXException("Invalid scope provided: " + scope + "! Only " + Arrays.toString(DataConstants.allScopes()) + " allowed!", GeoSensorXErrorCode.INVALID_ARGUMENTS);
        }

        ObjectNode request = mapper.createObjectNode();
        request.put("method", GET_OWNER_DEVICE_ATTRIBUTES);
        ObjectNode params = mapper.createObjectNode();
        params.put("keys", keysStr == null ? "" : keysStr);
        params.put("scope", scope);
        params.put("deviceName", deviceName);
        params.put("getAttributesFromCustomerIfAbsent", getAttributesFromCustomerIfAbsent);
        request.set("params", params);

        try {
            return geoSensorXService.processDeviceRestApiRequestToRuleEngine(jwtToken, deviceName, request, restApiTimeout);
        } catch (Exception e) {
            throw new GeoSensorXException("Failed to process request due to: " + e + "!", GeoSensorXErrorCode.GENERAL);
        }
    }

    @RequestMapping(value = "/owner/values/attributes", method = RequestMethod.GET)
    @ResponseBody
    public DeferredResult<ResponseEntity> getOwnerAttributes(@RequestParam(name = "keys", required = false) String keysStr,
                                                             @RequestParam(name = "restApiTimeout", required = false, defaultValue = "15000") long restApiTimeout,
                                                             HttpServletRequest httpServletRequest) throws GeoSensorXException {

        String jwtToken = getJwtTokenFromRequest(httpServletRequest);

        ObjectNode request = mapper.createObjectNode();
        request.put("method", GET_OWNER_ATTRIBUTES);
        ObjectNode params = mapper.createObjectNode();
        params.put("keys", keysStr == null ? "" : keysStr);
        request.set("params", params);

        try {
            return geoSensorXService.processOwnerRestApiRequestToRuleEngine(jwtToken, request, restApiTimeout);
        } catch (Exception e) {
            throw new GeoSensorXException("Failed to process request due to: " + e + "!", GeoSensorXErrorCode.GENERAL);
        }
    }

    @RequestMapping(value = "/owner/device/names", method = RequestMethod.GET)
    @ResponseBody
    public DeferredResult<ResponseEntity> getOwnerDeviceNames(@RequestParam(name = "allDevices", required = false, defaultValue = "false") boolean allDevices,
                                                              @RequestParam(name = "deviceNameFilter", required = false) String deviceNameFilter,
                                                              @RequestParam(name = "deviceTypeFilter", required = false) String deviceTypeFilter,
                                                              @RequestParam(name = "restApiTimeout", required = false, defaultValue = "15000") long restApiTimeout,
                                                              HttpServletRequest httpServletRequest) throws GeoSensorXException {

        String jwtToken = getJwtTokenFromRequest(httpServletRequest);

        ObjectNode request = mapper.createObjectNode();
        request.put("method", GET_OWNER_DEVICE_NAMES);
        ObjectNode params = mapper.createObjectNode();
        params.put("allDevices", allDevices);
        if (!StringUtils.isEmpty(deviceNameFilter)) {
            params.put("deviceNameFilter", deviceNameFilter);
        }
        if (!StringUtils.isEmpty(deviceTypeFilter)) {
            params.put("deviceTypeFilter", deviceTypeFilter);
        }
        request.set("params", params);

        try {
            return geoSensorXService.processOwnerRestApiRequestToRuleEngine(jwtToken, request, restApiTimeout);
        } catch (Exception e) {
            throw new GeoSensorXException("Failed to process request due to: " + e + "!", GeoSensorXErrorCode.GENERAL);
        }
    }

    @RequestMapping(value = "/owner/values/attributes", method = RequestMethod.POST)
    @ResponseBody
    public DeferredResult<ResponseEntity> saveOwnerAttributes(@RequestParam(name = "restApiTimeout", required = false, defaultValue = "15000") long restApiTimeout,
                                                              @RequestBody String requestBody,
                                                              HttpServletRequest httpServletRequest) throws GeoSensorXException {

        String jwtToken = getJwtTokenFromRequest(httpServletRequest);

        JsonNode payload;
        try {
            payload = mapper.readTree(requestBody);
        } catch (IOException e) {
            throw new GeoSensorXException("Invalid request body structure: " + requestBody + "! Reason: " + e.getMessage(), GeoSensorXErrorCode.BAD_REQUEST_PARAMS);
        }
        ObjectNode request = mapper.createObjectNode();
        request.put("method", SAVE_OWNER_ATTRIBUTES);
        ObjectNode params = mapper.createObjectNode();
        params.set("request", payload);
        request.set("params", params);

        try {
            return geoSensorXService.processOwnerRestApiRequestToRuleEngine(jwtToken, request, restApiTimeout);
        } catch (Exception e) {
            throw new GeoSensorXException("Failed to process request due to: " + e + "!", GeoSensorXErrorCode.GENERAL);
        }
    }

    @RequestMapping(value = "/{deviceName}/telemetry", method = RequestMethod.POST)
    @ResponseBody
    public DeferredResult<ResponseEntity> postTelemetry(@PathVariable("deviceName") String deviceName,
                                                        @RequestParam(name = "restApiTimeout", required = false, defaultValue = "15000") long restApiTimeout,
                                                        @RequestBody String requestBody,
                                                        HttpServletRequest httpServletRequest) throws GeoSensorXException {

        String jwtToken = getJwtTokenFromRequest(httpServletRequest);

        JsonNode payload;
        try {
            payload = mapper.readTree(requestBody);
        } catch (IOException e) {
            throw new GeoSensorXException("Invalid request body structure: " + requestBody + "! Reason: " + e.getMessage(), GeoSensorXErrorCode.BAD_REQUEST_PARAMS);
        }

        ObjectNode params = mapper.createObjectNode();
        params.put("deviceName", deviceName);
        params.put("ts", System.currentTimeMillis());
        params.set("payload", payload);

        ObjectNode request = mapper.createObjectNode();
        request.put("method", POST_TELEMETRY);
        request.set("params", params);

        try {
            return geoSensorXService.processDeviceRestApiRequestToRuleEngine(jwtToken, deviceName, request, restApiTimeout);
        } catch (Exception e) {
            throw new GeoSensorXException("Failed to process request due to: " + e + "!", GeoSensorXErrorCode.GENERAL);
        }
    }

    private String getJwtTokenFromRequest(HttpServletRequest httpServletRequest) throws GeoSensorXException {
        String jwtToken = httpServletRequest.getHeader("x-authorization");

        if (StringUtils.isEmpty(jwtToken)) {
            throw new GeoSensorXException("Invalid JwtToken: " + jwtToken + "!", GeoSensorXErrorCode.UNAUTHORIZED);
        }
        return jwtToken;
    }

    private void putDateToParams(String date, ObjectNode params, String keyName) throws GeoSensorXException {
        try {
            OffsetDateTime odt = OffsetDateTime.parse(date);
            Instant instant = odt.toInstant();
            params.put(keyName, instant.toEpochMilli());
        } catch (DateTimeParseException e) {
            throw new GeoSensorXException("Failed to parse " + keyName + " parameter value due to: " + e + "!", GeoSensorXErrorCode.INVALID_ARGUMENTS);
        }
    }

    @RequestMapping(value = "/config/set/{deviceName}", method = RequestMethod.POST)
    @ResponseBody
    public DeferredResult<ResponseEntity> processConfigSetRequest(@PathVariable("deviceName") String deviceName,
                                                            @RequestParam(name = "retries", required = false, defaultValue = "0") int retries,
                                                            @RequestParam(name = "rpcExpirationTime", required = false, defaultValue = "126227808000") long rpcExpirationTime,
                                                            @RequestParam(name = "restApiTimeout", required = false, defaultValue = "15000") long restApiTimeout,
                                                            @RequestBody String request,
                                                            HttpServletRequest httpServletRequest) throws GeoSensorXException {
        String jwtToken = getJwtTokenFromRequest(httpServletRequest);
        ObjectNode params;
        try {
            params = (ObjectNode) mapper.readTree(request);
        } catch (JsonProcessingException e) {
            throw new GeoSensorXException("Failed to process config set request due to: " + e + "!", GeoSensorXErrorCode.GENERAL);
        }
        if (params == null || params.isNull() || params.isEmpty()) {
            throw new GeoSensorXException("Invalid config set request body structure: " + request + "!", GeoSensorXErrorCode.BAD_REQUEST_PARAMS);
        }
        ObjectNode configSetRequest = mapper.createObjectNode();
        configSetRequest.put("method", CONFIG_SET);
        params.put("retries", retries);
        if (rpcExpirationTime > 0) {
            params.put("expirationTime", System.currentTimeMillis() + rpcExpirationTime);
        }
        if (params.has("request")) {
            params.put("request", JacksonUtil.toString(params.get("request")));
        }
        if (params.has("webhookConfig")) {
            ObjectNode webhookConfig = (ObjectNode) params.get("webhookConfig");
            webhookConfig.forEach(config -> {
                ObjectNode value = (ObjectNode) config;
                value.put("valueFrom", "user");
            });
            params.put("webhookConfig", JacksonUtil.toString(webhookConfig));
        }
        configSetRequest.set("params", params);
        try {
            return geoSensorXService.processDeviceRestApiRequestToRuleEngine(jwtToken, deviceName, configSetRequest, restApiTimeout);
        } catch (Exception e) {
            throw new GeoSensorXException("Failed to process request due to: " + e + "!", GeoSensorXErrorCode.GENERAL);
        }
    }

    @RequestMapping(value = "/config/get/{deviceName}", method = RequestMethod.POST)
    @ResponseBody
    public DeferredResult<ResponseEntity> processConfigGetRequest(@PathVariable("deviceName") String deviceName,
                                                                  @RequestParam(name = "retries", required = false, defaultValue = "0") int retries,
                                                                  @RequestParam(name = "rpcExpirationTime", required = false, defaultValue = "126227808000") long rpcExpirationTime,
                                                                  @RequestParam(name = "restApiTimeout", required = false, defaultValue = "15000") long restApiTimeout,
                                                                  HttpServletRequest httpServletRequest) throws GeoSensorXException {
        String jwtToken = getJwtTokenFromRequest(httpServletRequest);
        ObjectNode params = mapper.createObjectNode();

        return null;
    }

    @RequestMapping(value = "/livestream/{deviceName}", method = RequestMethod.POST)
    @ResponseBody
    public DeferredResult<ResponseEntity> setLiveStream(@PathVariable("deviceName") String deviceName,
                                                        @RequestParam(name = "restApiTimeout", required = false, defaultValue = "15000") long restApiTimeout,
                                                        @RequestBody String requestBody,
                                                        HttpServletRequest httpServletRequest) throws GeoSensorXException {
        DeferredResult<ResponseEntity> result = new DeferredResult<>();
        String jwtToken = getJwtTokenFromRequest(httpServletRequest);
        JsonNode payload;

        try {
            payload = mapper.readTree(requestBody);

            if (payload.has("cmd")) {
                if (!payload.get("cmd").textValue().equalsIgnoreCase("start") && !payload.get("cmd").textValue().equalsIgnoreCase("stop")) {
                    result.setErrorResult(new ResponseEntity<>("cmd is invalid", HttpStatus.INTERNAL_SERVER_ERROR));
                    return result;
                }
            }
            if (payload.has("channel")) {
                if (payload.get("channel").intValue() != 0 && payload.get("channel").intValue() != 1) {
                    result.setErrorResult(new ResponseEntity<>("channel is invalid", HttpStatus.INTERNAL_SERVER_ERROR));
                    return result;
                }
            }
            if (payload.has("interval")) {
                if (payload.get("interval").intValue() != 0 && payload.get("interval").intValue() != 1) {
                    result.setErrorResult(new ResponseEntity<>("interval is invalid", HttpStatus.INTERNAL_SERVER_ERROR));
                    return result;
                }
            }
        } catch (IOException e) {
            throw new GeoSensorXException("Invalid request body structure: " + requestBody + "! Reason: " + e.getMessage(), GeoSensorXErrorCode.BAD_REQUEST_PARAMS);
        }

        ObjectNode params = mapper.createObjectNode();
        params.put("ts", System.currentTimeMillis());
        params.set("payload", payload);

        ObjectNode request = mapper.createObjectNode();
        request.put("method", POST_TELEMETRY);
        request.set("params", params);

        try {
            ObjectNode rpcRequestBody;
            DeferredResult<ResponseEntity> rpcResult;
            ResponseEntity<?> rpcResponse;

            switch (payload.get("cmd").textValue()) {
                case "start":
                    DeferredResult<ResponseEntity> createResult = geoSensorXService.processCreateLiveStream(jwtToken, deviceName, request, restApiTimeout);
                    ResponseEntity<?> createResponse = (ResponseEntity<?>) createResult.getResult();

                    if (createResponse.getStatusCode() == HttpStatus.OK) {
                        ResponseEntity<ObjectNode> createResponseEntity = (ResponseEntity<ObjectNode>) createResult.getResult();

                        rpcRequestBody = mapper.createObjectNode();
                        rpcRequestBody.put("method", "stream-vid");
                        rpcRequestBody.put("params", "start," + (payload.has("channel") ? payload.get("channel").intValue() : "") + "," + (payload.has("interval") ? payload.get("interval").intValue() : "") + ",rtmp://global-live.mux.com:5222/app/" + createResponseEntity.getBody().get("data").get("streamKey").textValue() + "," + (payload.has("duration") ? payload.get("duration").intValue() : ""));

                        rpcResult = processRpcRequest(deviceName, 0, 126227808, restApiTimeout, rpcRequestBody.toPrettyString(),httpServletRequest);
                        rpcResponse = (ResponseEntity<?>) rpcResult.getResult();

                        if (rpcResponse.getStatusCode().is2xxSuccessful()) {
                            ResponseEntity<ObjectNode> rpcResponseEntity = (ResponseEntity<ObjectNode>) rpcResult.getResult();

                            ObjectNode newBody = mapper.createObjectNode();
                            ObjectNode newData = mapper.createObjectNode();
                            newData.put("rpcId", rpcResponseEntity.getBody().get("rpcId").textValue());
                            newData.put("playId", createResponseEntity.getBody().get("data").get("playId").textValue());
                            newData.put("status", "SUCCESS");
                            newData.put("streamURL", "https://stream.geosensox.ai");
                            newBody.set("data", newData);

                            result.setResult(new ResponseEntity<>(newData, HttpStatus.OK));
                            return result;
                        } else {
                            return rpcResult;
                        }
                    } else {
                        return createResult;
                    }
                case "stop":
                    rpcRequestBody = mapper.createObjectNode();
                    rpcRequestBody.put("method", "stream-vid");
                    rpcRequestBody.put("params", "stop");

                    rpcResult = processRpcRequest(deviceName, 0, 126227808, restApiTimeout, rpcRequestBody.toPrettyString(),httpServletRequest);
                    rpcResponse = (ResponseEntity<?>) rpcResult.getResult();

                    if (rpcResponse.getStatusCode().is2xxSuccessful()) {
                        ResponseEntity<ObjectNode> rpcResponseEntity = (ResponseEntity<ObjectNode>) rpcResult.getResult();

                        ObjectNode newBody = mapper.createObjectNode();
                        ObjectNode newData = mapper.createObjectNode();
                        newData.put("rpcId", rpcResponseEntity.getBody().get("rpcId").textValue());
                        newData.put("status", "SUCCESS");
                        newBody.set("data", newData);

                        result.setResult(new ResponseEntity<>(newData, HttpStatus.OK));

                        return result;
                    } else {
                        return rpcResult;
                    }
                case "delete":
                    DeferredResult<ResponseEntity> deleteResult = geoSensorXService.processDeleteLiveStream(jwtToken, deviceName, restApiTimeout);
                    ResponseEntity<?> deleteResponse = (ResponseEntity<?>) deleteResult.getResult();

                    if (deleteResponse.getStatusCode() == HttpStatus.OK) {
                        ResponseEntity<ObjectNode> deleteResponseEntity = (ResponseEntity<ObjectNode>) deleteResult.getResult();

                        rpcRequestBody = mapper.createObjectNode();
                        rpcRequestBody.put("method", "stream-vid");
                        rpcRequestBody.put("params", "stop");

                        try {
                            rpcResult = processRpcRequest(deviceName, 0, 126227808, restApiTimeout, rpcRequestBody.toPrettyString(),httpServletRequest);
                            rpcResponse = (ResponseEntity<?>) rpcResult.getResult();

                            if (!rpcResponse.getStatusCode().is2xxSuccessful()) {
                                return rpcResult;
                            }
                        } catch (Exception e) {
                            throw new GeoSensorXException("Failed to process request due to: " + e + "!", GeoSensorXErrorCode.GENERAL);
                        }

                        ObjectNode newBody = mapper.createObjectNode();
                        ObjectNode newData = mapper.createObjectNode();
                        newData.put("rpcId", deleteResponseEntity.getBody().get("data").get("rpcId").textValue());
                        newData.put("status", "SUCCESS");
                        newBody.set("data", newData);

                        result.setResult(new ResponseEntity<>(newData, HttpStatus.OK));

                        return result;
                    } else {
                        return deleteResult;
                    }
            }
        } catch (Exception e) {
            throw new GeoSensorXException("Failed to process request due to: " + e + "!", GeoSensorXErrorCode.GENERAL);
        }

        return null;
    }

    @RequestMapping(value = "/livestream/{deviceName}", method = RequestMethod.GET)
    @ResponseBody
    public DeferredResult<ResponseEntity> getLiveStream(@PathVariable("deviceName") String deviceName,
                                                        @RequestParam(name = "rpcId", required = true, defaultValue = "0") String rpcId,
                                                        @RequestBody String requestBody,
                                                        HttpServletRequest httpServletRequest) throws GeoSensorXException {
        String jwtToken = getJwtTokenFromRequest(httpServletRequest);
        JsonNode payload;
        try {
            payload = mapper.readTree(requestBody);
        } catch (IOException e) {
            throw new GeoSensorXException("Invalid request body structure: " + requestBody + "! Reason: " + e.getMessage(), GeoSensorXErrorCode.BAD_REQUEST_PARAMS);
        }

        ObjectNode params = mapper.createObjectNode();
        params.put("deviceName", deviceName);
        params.put("ts", System.currentTimeMillis());
        params.set("payload", payload);

        ObjectNode request = mapper.createObjectNode();
        request.put("method", POST_TELEMETRY);
        request.set("params", params);

        return geoSensorXService.processGetRpcById(jwtToken, rpcId, 15000);
    }
}
