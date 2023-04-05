package com.geosensorx.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.geosensorx.cache.JwtTokenCache;
import com.geosensorx.cache.JwtTokenCacheKey;
import com.geosensorx.data.DeviceEntity;
import com.geosensorx.data.mux.LiveStream;
import com.geosensorx.data.mux.MuxInfoData;
import com.geosensorx.data.mux.MuxListData;
import com.geosensorx.http.client.MuxGeoSensorXHttpClient;
import com.geosensorx.http.client.S3GeoSensorXHttpClient;
import com.geosensorx.http.client.TbGeoSensorXHttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.context.request.async.DeferredResult;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.rpc.Rpc;
import org.thingsboard.server.common.data.rpc.RpcStatus;
import org.thingsboard.server.common.data.security.Authority;
import org.thingsboard.server.common.data.security.DeviceCredentials;
import springfox.documentation.spring.web.json.Json;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Period;
import java.util.Date;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Calendar;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.geosensorx.controller.GeoSensorXController.GET_COMPLETED_RPC_DATA_METHOD;

@Slf4j
@Service
public class DefaultGeoSensorXService implements GeoSensorXService {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private TbGeoSensorXHttpClient tbhttpClient;

    @Autowired
    private S3GeoSensorXHttpClient s3HttpClient;

    @Autowired
    private MuxGeoSensorXHttpClient muxhttpClient;

    private JwtTokenCache jwtTokenCache;
    private ExecutorService executorService;

    @PostConstruct
    public void init() {
        jwtTokenCache = new JwtTokenCache();
        executorService = Executors.newCachedThreadPool();
    }

    @PreDestroy
    public void destroy() {
        if (executorService != null) {
            executorService.shutdown();
        }
    }


    @Override
    public DeferredResult<ResponseEntity> processLogin(JsonNode loginRequest) {
        DeferredResult<ResponseEntity> result = new DeferredResult<>();
        JwtTokenCacheKey tokenKey = new JwtTokenCacheKey(loginRequest.get("username").asText(), loginRequest.get("password").asText());
        CompletableFuture<JsonNode> tokenValueFuture = jwtTokenCache.getIfPresent(tokenKey);
        if (tokenValueFuture == null) {
            doProcessLogin(loginRequest, result, tokenKey);
        } else {
            tokenValueFuture.whenCompleteAsync((success, err) -> {
                if (err != null) {
                    log.info("Failed to fetch JWT token from cache for user: {}", tokenKey.getUserName());
                    doProcessLogin(loginRequest, result, tokenKey);
                } else {
                    log.info("Successfully fetched JWT token from cache for user: {}", tokenKey.getUserName());
                    JsonNode tokenBody = getJwtTokenBodyBase64(success.get("token").asText());
                    long expMillis = TimeUnit.SECONDS.toMillis(tokenBody.get("exp").asLong());
                    if (expMillis < System.currentTimeMillis()) {
                        log.info("Token expired! Updating value in cache for user: {}", tokenKey.getUserName());
                        doProcessLogin(loginRequest, result, tokenKey);
                    } else {
                        result.setResult(new ResponseEntity<>(success, HttpStatus.OK));
                    }
                }
            }, executorService);
        }
        return result;
    }

    private void doProcessLogin(JsonNode loginRequest, DeferredResult<ResponseEntity> result, JwtTokenCacheKey tokenKey) {
        String endPointURL = tbhttpClient.getBaseUrl() + "/api/auth/login";
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        headers.add("Accept", "application/json");
        HttpEntity<JsonNode> httpEntity = new HttpEntity<>(loginRequest, headers);
        log.debug("Try to process login request: {}, requestTime: {}", endPointURL, System.currentTimeMillis());
        ListenableFuture<ResponseEntity<JsonNode>> future = tbhttpClient.exchange(
                endPointURL, HttpMethod.POST, httpEntity, JsonNode.class);
        future.addCallback(new ListenableFutureCallback<>() {
            @Override
            public void onFailure(Throwable throwable) {
                log.error("Failed to process request: {} due to: {}", endPointURL, throwable.getMessage());
                if (throwable instanceof HttpClientErrorException) {
                    result.setErrorResult(new ResponseEntity<>(throwable.getMessage(), ((HttpClientErrorException) throwable).getStatusCode()));
                } else {
                    result.setErrorResult(new ResponseEntity<>(throwable.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR));
                }
            }

            @Override
            public void onSuccess(ResponseEntity<JsonNode> responseEntity) {
                log.info("Successfully processed login request for user: {}", tokenKey.getUserName());
                jwtTokenCache.put(tokenKey, responseEntity.getBody());
                result.setResult(new ResponseEntity<>(responseEntity.getBody(), HttpStatus.OK));
            }
        });
    }

    @Override
    public DeferredResult<ResponseEntity> processRefreshToken(JsonNode refreshToken) {
        DeferredResult<ResponseEntity> result = new DeferredResult<>();
        String endPointURL = tbhttpClient.getBaseUrl() + "/api/auth/token";
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        headers.add("Accept", "application/json");
        HttpEntity<JsonNode> httpEntity = new HttpEntity<>(refreshToken, headers);
        log.debug("Try to process refresh token request: {}, requestTime: {}", endPointURL, System.currentTimeMillis());
        ListenableFuture<ResponseEntity<JsonNode>> future = tbhttpClient.exchange(
                endPointURL, HttpMethod.POST, httpEntity, JsonNode.class);
        future.addCallback(new ListenableFutureCallback<>() {
            @Override
            public void onSuccess(ResponseEntity<JsonNode> responseEntity) {
                log.info("Successfully processed refresh token request");
                result.setResult(new ResponseEntity<>(responseEntity.getBody(), HttpStatus.OK));
            }

            @Override
            public void onFailure(Throwable throwable) {
                log.error("Failed to process request: {} due to: {}", endPointURL, throwable.getMessage());
                if (throwable instanceof HttpClientErrorException) {
                    result.setErrorResult(new ResponseEntity<>(throwable.getMessage(), ((HttpClientErrorException) throwable).getStatusCode()));
                } else {
                    result.setErrorResult(new ResponseEntity<>(throwable.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR));
                }
            }
        });
        return result;
    }

    @Override
    public DeferredResult<ResponseEntity> processSaveDevice(String jwtToken, DeviceEntity deviceEntity) {
        Device device;
        DeferredResult<ResponseEntity> deviceDeferredResult = processGetDeviceByName(jwtToken, deviceEntity.getName());
        if (deviceDeferredResult.hasResult()) {
            try {
                ResponseEntity<?> deviceResponse = (ResponseEntity<?>) deviceDeferredResult.getResult();
                if (deviceResponse.getStatusCode().is2xxSuccessful()) {
                    ResponseEntity<Device> deviceResponseEntity = (ResponseEntity<Device>) deviceDeferredResult.getResult();
                    if (deviceResponseEntity != null && deviceResponseEntity.getBody() != null) {
                        device = updateDeviceFields(deviceResponseEntity.getBody(), deviceEntity);
                    } else {
                        device = updateDeviceFields(new Device(), deviceEntity);
                    }
                } else if (deviceResponse.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
                    device = updateDeviceFields(new Device(), deviceEntity);
                } else {
                    return deviceDeferredResult;
                }
            } catch (Exception e) {
                Throwable cause = e.getCause();
                DeferredResult<ResponseEntity> errorDeferredResult = new DeferredResult<>();
                errorDeferredResult.setErrorResult(onFailureHandler(cause, "processSaveDevice"));
                return errorDeferredResult;
            }
        } else {
            device = updateDeviceFields(new Device(), deviceEntity);
        }
        return saveDevice(device, deviceEntity.getAccessToken(), jwtToken);
    }

    @Override
    public DeferredResult<ResponseEntity> processDeviceRestApiRequestToRuleEngine(String jwtToken, String deviceName, JsonNode requestBody, long restApiTimeout) {
        DeferredResult<ResponseEntity> deviceDeferredResult = processGetDeviceByName(jwtToken, deviceName);
        if (deviceDeferredResult.hasResult()) {
            try {
                ResponseEntity<?> deviceResponse = (ResponseEntity<?>) deviceDeferredResult.getResult();
                if (deviceResponse.getStatusCode().is2xxSuccessful()) {
                    ResponseEntity<Device> deviceResponseEntity = (ResponseEntity<Device>) deviceResponse;
                    if (deviceResponseEntity.getBody() != null) {
                        DeferredResult<ResponseEntity> result = new DeferredResult<>();
                        Device device = deviceResponseEntity.getBody();
                        String endPointURL = tbhttpClient.getBaseUrl() + "/api/rule-engine/DEVICE/" + device.getUuidId() + "/" + restApiTimeout + "/RestApiHandler";
                        log.debug("Jwt Token: [{}]", jwtToken);
                        HttpHeaders headers = getBearerTokenHttpHeaders(jwtToken);
                        ObjectNode params = (ObjectNode) requestBody.get("params");
                        params.put("ownerId", device.getOwnerId().toString());
                        params.put("ownerType", device.getOwnerId().getEntityType().name());
                        params.put("deviceType", device.getType());
                        ObjectNode finalRequestBody = (ObjectNode) requestBody;
                        finalRequestBody.set("params", params);
                        HttpEntity<JsonNode> httpEntity = new HttpEntity<>(finalRequestBody, headers);
                        log.debug("Try to process device rest api request: {}, requestTime: {}", endPointURL, System.currentTimeMillis());
                        ListenableFuture<ResponseEntity<JsonNode>> future = tbhttpClient.exchange(
                                endPointURL, HttpMethod.POST, httpEntity, JsonNode.class);

                        ResponseEntity<JsonNode> data = future.get();
                        if (data.getStatusCode().is2xxSuccessful()) {
                            log.info("Successfully processed request: {}, responseTime: {}", finalRequestBody, System.currentTimeMillis());
                            JsonNode body = data.getBody();
                            result.setResult(onSuccessResponseHandle(body));
                        } else {
                            log.info("Failed to process request: {} due to: {}, responseTime: {}", finalRequestBody, data.getBody(), System.currentTimeMillis());
                            result.setErrorResult(data);
                        }

                        return result;
                    } else {
                        return deviceDeferredResult;
                    }
                } else {
                    return deviceDeferredResult;
                }
            } catch (Exception e) {
                Throwable cause = e.getCause();
                DeferredResult<ResponseEntity> errorDeferredResult = new DeferredResult<>();
                errorDeferredResult.setErrorResult(onFailureHandler(cause, "processDeviceRestApiRequestToRuleEngine"));
                return errorDeferredResult;
            }
        } else {
            return deviceDeferredResult;
        }
    }

    private ResponseEntity onSuccessResponseHandle(JsonNode body) {
        if (body != null && !body.isNull()) {
            if (!body.isEmpty()) {
                if (body.has("error")) {
                    return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
                } else if (body.has("response")) {
                    JsonNode responseBody = body.get("response");
                    if (responseBody.isTextual()) {
                        return new ResponseEntity<>(responseBody.textValue(), HttpStatus.OK);
                    } else if (responseBody.isNumber()) {
                        return new ResponseEntity<>(responseBody.numberValue(), HttpStatus.OK);
                    } else {
                        return new ResponseEntity<>(responseBody, HttpStatus.OK);
                    }
                } else {
                    return new ResponseEntity<>(body, HttpStatus.OK);
                }
            }
        }
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @Override
    public DeferredResult<ResponseEntity> processOwnerRestApiRequestToRuleEngine(String jwtToken, JsonNode requestBody, long restApiTimeout) {
        DeferredResult<ResponseEntity> result = new DeferredResult<>();
        JsonNode jwtTokenBody = getJwtTokenBodyBase64(jwtToken);
        if (jwtTokenBody != null && !jwtTokenBody.isNull() && jwtTokenBody.hasNonNull("scopes") && jwtTokenBody.get("scopes").isArray() && jwtTokenBody.get("scopes").size() > 0) {
            String ownerId = null;
            String entityType = null;
            if (jwtTokenBody.get("scopes").get(0).asText().equals(Authority.CUSTOMER_USER.name())) {
                if (jwtTokenBody.hasNonNull("customerId")) {
                    ownerId = jwtTokenBody.get("customerId").asText();
                    entityType = EntityType.CUSTOMER.name();
                }
            } else if (jwtTokenBody.get("scopes").get(0).asText().equals(Authority.TENANT_ADMIN.name())) {
                if (jwtTokenBody.hasNonNull("tenantId")) {
                    ownerId = jwtTokenBody.get("tenantId").asText();
                    entityType = EntityType.TENANT.name();
                }
            } else {
                result.setErrorResult(new ResponseEntity<>("You don't have permission to perform this operation!", HttpStatus.FORBIDDEN));
            }
            if (ownerId == null && entityType == null) {
                result.setErrorResult(new ResponseEntity<>("Invalid JWT token!", HttpStatus.FORBIDDEN));
            } else {
                String endPointURL = tbhttpClient.getBaseUrl() + "/api/rule-engine/" + entityType + "/" + ownerId + "/" + restApiTimeout + "/RestApiHandler";
                HttpHeaders headers = getDefaultHttpHeaders(jwtToken);
                HttpEntity<JsonNode> httpEntity = new HttpEntity<>(requestBody, headers);
                log.debug("Try to process owner rest api request: {}, requestTime: {}", endPointURL, System.currentTimeMillis());
                ListenableFuture<ResponseEntity<JsonNode>> future = tbhttpClient.exchange(
                        endPointURL, HttpMethod.POST, httpEntity, JsonNode.class);
                future.addCallback(new ListenableFutureCallback<>() {
                    @Override
                    public void onFailure(Throwable throwable) {
                        log.info("Failed to process request: {} due to: {}", requestBody, throwable.getMessage());
                        result.setErrorResult(onFailureHandler(throwable, endPointURL));
                    }

                    @Override
                    public void onSuccess(ResponseEntity<JsonNode> responseEntity) {
                        log.info("Successfully processed request: {}", requestBody);
                        JsonNode body = responseEntity.getBody();
                        result.setResult(onSuccessResponseHandle(body));
                    }
                });
            }
        } else {
            result.setErrorResult(new ResponseEntity<>("Invalid JWT token!", HttpStatus.FORBIDDEN));
        }
        return result;
    }

    @Override
    public DeferredResult<ResponseEntity> processGetDeviceByName(String jwtToken, String deviceName) {
        DeferredResult<ResponseEntity> result = new DeferredResult<>();
        String endPointURL = tbhttpClient.getBaseUrl() + "/api/user/devices?pageSize=1000&page=0&textSearch=" + deviceName;
        HttpHeaders headers = getBearerTokenHttpHeaders(jwtToken);
        HttpEntity<PageData<Device>> httpEntity = new HttpEntity<>(headers);
        log.debug("Try to process get user devices request: {}, requestTime: {}", endPointURL, System.currentTimeMillis());
        ListenableFuture<ResponseEntity<PageData<Device>>> deviceFuture = tbhttpClient.exchange(
                endPointURL, HttpMethod.GET, httpEntity, new ParameterizedTypeReference<>() {
                });
        try {
            ResponseEntity<PageData<Device>> data = deviceFuture.get();
            if (data.getStatusCode().is2xxSuccessful()) {
                if (data.getBody().getTotalElements() > 0) {
                    Optional<Device> deviceOptional = data
                            .getBody()
                            .getData()
                            .stream()
                            .filter(d -> d.getName().equals(deviceName))
                            .findFirst();
                    if (deviceOptional.isPresent()) {
                        result.setResult(new ResponseEntity(deviceOptional.get(), HttpStatus.OK));
                    } else {
                        result.setErrorResult(deviceNotFoundResponseEntity(deviceName));
                    }
                } else {
                    result.setErrorResult(deviceNotFoundResponseEntity(deviceName));
                }
            } else {
                result.setErrorResult(data);
            }
        } catch (ExecutionException | InterruptedException e) {
            Throwable cause = e.getCause();
            log.error("[{}] Failed to find the device with the name due to: ", deviceName, cause);
            result.setErrorResult(onFailureHandler(cause, endPointURL));
        }

        return result;
    }

    @Override
    public DeferredResult<ResponseEntity> processGetDeviceIdByName(String jwtToken, String deviceName) {
        DeferredResult<ResponseEntity> deviceDeferredResult = processGetDeviceByName(jwtToken, deviceName);
        if (deviceDeferredResult.hasResult()) {
            try {
                ResponseEntity<?> deviceResponse = (ResponseEntity<?>) deviceDeferredResult.getResult();
                if (deviceResponse.getStatusCode().is2xxSuccessful()) {
                    ResponseEntity<Device> deviceResponseEntity = (ResponseEntity<Device>) deviceDeferredResult.getResult();
                    if (deviceResponseEntity != null && deviceResponseEntity.getBody() != null) {
                        DeferredResult<ResponseEntity> deviceIdResult = new DeferredResult<>();
                        deviceIdResult.setResult(new ResponseEntity<>(deviceResponseEntity.getBody().getUuidId(), HttpStatus.OK));
                        return deviceIdResult;
                    } else {
                        return deviceDeferredResult;
                    }
                } else {
                    return deviceDeferredResult;
                }
            } catch (Exception e) {
                Throwable cause = e.getCause();
                DeferredResult<ResponseEntity> errorDeferredResult = new DeferredResult<>();
                errorDeferredResult.setErrorResult(onFailureHandler(cause, "processGetDeviceIdByName"));
                return errorDeferredResult;
            }
        } else {
            return deviceDeferredResult;
        }
    }

    @Override
    public DeferredResult<ResponseEntity> processGetPresignedUrlDownloadExtractedMedia(String deviceName, String deviceToken, String fileName) {
        DeferredResult<ResponseEntity> result = new DeferredResult<>();
        HttpEntity<String> httpEntity = new HttpEntity<>(getS3TextHttpHeaders());
        String s3EndPoint = buildPresignedUrlDownloadEndpoint(deviceName, deviceToken, fileName);
        log.debug("Try to process get presigned url download extracted media  request: {}, requestTime: {}", s3EndPoint, System.currentTimeMillis());
        ListenableFuture<ResponseEntity<String>> presignedUrlDownloadExtractedFuture = s3HttpClient.exchange(
                s3EndPoint, HttpMethod.GET, httpEntity, String.class);
        presignedUrlDownloadExtractedFuture.addCallback(new ListenableFutureCallback<>() {
            @Override
            public void onFailure(Throwable throwable) {
                result.setErrorResult(onFailureHandler(throwable, s3EndPoint));
            }

            @Override
            public void onSuccess(ResponseEntity<String> responseEntity) {
                log.info("Successfully processed request: {}", s3EndPoint);
                String redirectUrl = responseEntity.getBody();
                URI location = URI.create(redirectUrl);
                HttpHeaders responseHeaders = new HttpHeaders();
                responseHeaders.setLocation(location);
                ResponseEntity<?> resultResponse = new ResponseEntity<>(responseHeaders, HttpStatus.FOUND);
                result.setResult(resultResponse);
            }
        });
        return result;
    }

    private String buildPresignedUrlDownloadEndpoint(String deviceName, String deviceToken, String fileName) {
        String lowerCaseFileName = fileName.toLowerCase();
        if (lowerCaseFileName.endsWith(".jpg") || lowerCaseFileName.endsWith(".avi")) {
            return s3HttpClient.getBaseUrl() + "/api/media/" + deviceToken +
                    "/presignedUrlDownload?uniqueId=" + deviceName + "&mediaFileName=" + fileName;
        } else {
            return s3HttpClient.getBaseUrl() + "/api/media/" + deviceToken +
                    "/presignedUrlDownloadExtracted?uniqueId=" + deviceName + "&mediaFileName=" + fileName;
        }

    }

    @Override
    public DeferredResult<ResponseEntity> processGetRpcById(String jwtToken, String rpcId, long restApiTimeout) {
        DeferredResult<ResponseEntity> result = new DeferredResult<>();
        HttpHeaders headers = getDefaultHttpHeaders(jwtToken);
        HttpEntity<String> httpEntity = new HttpEntity<>(rpcId, headers);
        String getRpcByIdEndPointURL = tbhttpClient.getBaseUrl() + "/api/rpc/persistent/" + rpcId;
        log.debug("Try to process get rpc by id request: {}, requestTime: {}", getRpcByIdEndPointURL, System.currentTimeMillis());
        ListenableFuture<ResponseEntity<Rpc>> getRpcByIdFuture = tbhttpClient.exchange(
                getRpcByIdEndPointURL, HttpMethod.GET, httpEntity, Rpc.class);
        getRpcByIdFuture.addCallback(new ListenableFutureCallback<>() {
            @Override
            public void onSuccess(ResponseEntity<Rpc> rpcResponseEntity) {
                log.info("Successfully processed request: {}", getRpcByIdEndPointURL);
                Rpc rpc = rpcResponseEntity.getBody();
                if (rpc == null) {
                    result.setResult(new ResponseEntity<>(HttpStatus.NO_CONTENT));
                } else {
                    JsonNode rpcAddInfo = rpc.getAdditionalInfo();
                    if (rpc.getStatus().equals(RpcStatus.SUCCESSFUL)
                            && rpcAddInfo.has("reportCompletedStatusSupported")
                            && rpcAddInfo.get("reportCompletedStatusSupported").asBoolean(false)) {
                        String endPointURL = tbhttpClient.getBaseUrl() + "/api/rule-engine/DEVICE/" + rpc.getDeviceId() + "/" + restApiTimeout + "/RestApiHandler";
                        HttpHeaders headers = getDefaultHttpHeaders(jwtToken);
                        ObjectNode requestBody = mapper.createObjectNode();
                        requestBody.put("method", GET_COMPLETED_RPC_DATA_METHOD);
                        requestBody.put("rpcId", rpcId);
                        HttpEntity<JsonNode> httpEntity = new HttpEntity<>(requestBody, headers);
                        log.debug("Try to process device rest api request: {}, requestTime: {}", endPointURL, System.currentTimeMillis());
                        ListenableFuture<ResponseEntity<JsonNode>> future = tbhttpClient.exchange(
                                endPointURL, HttpMethod.POST, httpEntity, JsonNode.class);
                        future.addCallback(new ListenableFutureCallback<>() {
                            @Override
                            public void onFailure(Throwable throwable) {
                                log.info("Failed to process request: {} due to: {}, responseTime: {}", requestBody, throwable.getMessage(), System.currentTimeMillis());
                                result.setErrorResult(onFailureHandler(throwable, endPointURL));
                            }

                            @Override
                            public void onSuccess(ResponseEntity<JsonNode> responseEntity) {
                                log.info("Successfully processed request: {}, responseTime: {}", requestBody, System.currentTimeMillis());
                                JsonNode body = responseEntity.getBody();
                                result.setResult(onSuccessRpcCompletedResponseHandle(body, rpc));
                            }
                        });
                    } else {
                        result.setResult(new ResponseEntity<>(defaultRpcResponse(rpc), HttpStatus.OK));
                    }
                }
            }

            @Override
            public void onFailure(Throwable throwable) {
                result.setErrorResult(onFailureHandler(throwable, getRpcByIdEndPointURL));
            }
        });
        return result;
    }

    @Override
    public DeferredResult<ResponseEntity> processGetAttributes(String jwtToken, String deviceId) {
        DeferredResult<ResponseEntity> result = new DeferredResult<>();

        HttpHeaders headers = getBearerTokenHttpHeaders(jwtToken);
        HttpEntity<String> httpEntity = new HttpEntity<>(headers);
        String getAttributesEndPointURL = tbhttpClient.getBaseUrl() + "/api/plugins/telemetry/DEVICE/" + deviceId + "/values/attributes/SERVER_SCOPE?keys=stream_key,playback_id,created_at";
        log.debug("Try to process get attributes by device id request: {}, requestTime: {}", getAttributesEndPointURL, System.currentTimeMillis());
        ListenableFuture<ResponseEntity<ArrayList<JsonNode>>> getAttributesFuture = tbhttpClient.exchange(
                getAttributesEndPointURL, HttpMethod.GET, httpEntity, new ParameterizedTypeReference<>() {
                });

        try {
            ResponseEntity<ArrayList<JsonNode>> data = getAttributesFuture.get();

            result.setResult(data);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        return result;
    }

    @Override
    public DeferredResult<ResponseEntity> processSetAttributes(String jwtToken, String deviceId, JsonNode requestBody) {
        DeferredResult<ResponseEntity> result = new DeferredResult<>();

        HttpHeaders headers = getBearerTokenHttpHeaders(jwtToken);
        HttpEntity<JsonNode> httpEntity = new HttpEntity<>(requestBody, headers);
        String setAttributesEndPointURL = tbhttpClient.getBaseUrl() + "/api/plugins/telemetry/DEVICE/" + deviceId + "/attributes/SERVER_SCOPE";
        log.debug("Try to process set attributes by device id request: {}, requestTime: {}", setAttributesEndPointURL, System.currentTimeMillis());
        ListenableFuture<ResponseEntity<JsonNode>> setAttributesFuture = tbhttpClient.exchange(
                setAttributesEndPointURL, HttpMethod.POST, httpEntity, JsonNode.class);

        try {
            ResponseEntity<JsonNode> data = setAttributesFuture.get();

            result.setResult(data);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        return result;
    }

    @Override
    public DeferredResult<ResponseEntity> processCreateLiveStream(String jwtToken, String deviceName, JsonNode requestBody, long restApiTimeout) {
        DeferredResult<ResponseEntity> deferredResult = processGetDeviceByName(jwtToken, deviceName);

        if (deferredResult.hasResult()) {
            try {
                ResponseEntity<?> deviceResponse = (ResponseEntity<?>) deferredResult.getResult();
                if (deviceResponse.getStatusCode().is2xxSuccessful()) {
                    DeferredResult<ResponseEntity> result = new DeferredResult<>();
                    ResponseEntity<Device> deviceResponseEntity = (ResponseEntity<Device>) deviceResponse;
                    if (deviceResponseEntity.getBody() != null) {
                        Device device = deviceResponseEntity.getBody();

                        DeferredResult<ResponseEntity> getAttributeResponse = processGetAttributes(jwtToken, device.getUuidId().toString());
                        ResponseEntity<ArrayList<JsonNode>> getAttributeResponseEntity = (ResponseEntity<ArrayList<JsonNode>>) getAttributeResponse.getResult();

                        if (getAttributeResponseEntity.getStatusCode().is2xxSuccessful()) {
                            ArrayList<JsonNode> attributeResult = getAttributeResponseEntity.getBody();

                            String stream_key = "";
                            String playback_id = "";
                            long created_at = 0;

                            Calendar c = Calendar.getInstance();
                            c.setTime(new Date());
                            c.add(Calendar.DATE, -5);
                            created_at = c.getTime().getTime();

                            for ( JsonNode atrributeInfo : attributeResult) {
                                switch (atrributeInfo.get("key").textValue()) {
                                    case "stream_key":
                                        stream_key = atrributeInfo.get("value").textValue();
                                        break;
                                    case "playback_id":
                                        playback_id = atrributeInfo.get("value").textValue();
                                        break;
                                    case "created_at":
                                        created_at = atrributeInfo.get("value").longValue();
                                        break;
                                }
                            }

                            long diffInMillies = Math.abs(new Date().getTime() - created_at * 1000);
                            long diff = TimeUnit.DAYS.convert(diffInMillies, TimeUnit.MILLISECONDS);

                            if (stream_key.isEmpty() || playback_id.isEmpty() || diff > 1) {
                                String endPointURL = muxhttpClient.getBaseUrl() + "/video/v1/live-streams";
                                HttpHeaders headers = getBasicAuthHttpHeaders(muxhttpClient.muxApiToken, muxhttpClient.muxApiSecret);
                                ObjectNode liveSteamRequest = mapper.createObjectNode();
                                liveSteamRequest.put("playback_policy", "public");
                                ObjectNode newAssetSettings = mapper.createObjectNode();
                                ArrayList<String> playbackPolicy = new ArrayList<>();
                                playbackPolicy.add("public");
                                newAssetSettings.putPOJO("playback_policy", playbackPolicy);
                                liveSteamRequest.set("new_asset_settings", newAssetSettings);
                                HttpEntity<JsonNode> httpEntity = new HttpEntity<>(liveSteamRequest, headers);
                                log.info("Try to process create a live stream request: {}, requestTime: {}", endPointURL, System.currentTimeMillis());
                                ListenableFuture<ResponseEntity<MuxInfoData<LiveStream>>> liveStreamFuture = muxhttpClient.exchange(
                                        endPointURL, HttpMethod.POST, httpEntity, new ParameterizedTypeReference<>() {
                                        });

                                ResponseEntity<MuxInfoData<LiveStream>> data = liveStreamFuture.get();

                                if (data.getStatusCode() == HttpStatus.CREATED) {
                                    ObjectNode responseNode = mapper.createObjectNode();
                                    LiveStream liveStreamInfo = data.getBody().getData();

                                    ObjectNode attributesRequestBody = mapper.createObjectNode();
                                    log.info("Mux stream key: " + liveStreamInfo.stream_key);
                                    log.info("Live stream playback id: " + liveStreamInfo.playback_ids.get(0).id);
                                    attributesRequestBody.put("stream_key", liveStreamInfo.stream_key);
                                    attributesRequestBody.put("playback_id", liveStreamInfo.playback_ids.get(0).id);
                                    attributesRequestBody.put("created_at", liveStreamInfo.created_at);
                                    DeferredResult<ResponseEntity> setAttributeResponse = processSetAttributes(jwtToken, device.getUuidId().toString(), attributesRequestBody);
                                    ResponseEntity<?> setAttributeResponseEntity = (ResponseEntity<?>) setAttributeResponse.getResult();

                                    if (setAttributeResponseEntity.getStatusCode().is2xxSuccessful()) {
                                        ObjectNode dataResult = mapper.createObjectNode();
                                        dataResult.put("playId", liveStreamInfo.playback_ids.get(0).id);
                                        dataResult.put("streamKey", liveStreamInfo.stream_key);
                                        responseNode.set("data", dataResult);

                                        result.setResult(new ResponseEntity<>(responseNode, HttpStatus.OK));
                                    } else {
                                        result.setResult(setAttributeResponseEntity);
                                    }
                                } else {
                                    result.setErrorResult(new ResponseEntity<>(data.getBody(), data.getStatusCode()));
                                }
                            }
                            else {
                                ObjectNode responseNode = mapper.createObjectNode();
                                ObjectNode dataResult = mapper.createObjectNode();
                                dataResult.put("playId", playback_id);
                                dataResult.put("streamKey", stream_key);
                                responseNode.set("data", dataResult);

                                result.setResult(new ResponseEntity<>(responseNode, HttpStatus.OK));
                            }

                        } else {
                            result.setErrorResult(getAttributeResponseEntity);
                        }

                        return result;
                    }
                }

                return deferredResult;
            } catch (Exception e) {
                Throwable cause = e.getCause();
                DeferredResult<ResponseEntity> errorDeferredResult = new DeferredResult<>();
                errorDeferredResult.setErrorResult(onFailureHandler(cause, "processDeviceRestApiRequestToRuleEngine"));
                return errorDeferredResult;
            }
        } else {
            return deferredResult;
        }
    }

    @Override
    public DeferredResult<ResponseEntity> processDeleteLiveStream(String jwtToken, String deviceName, long restApiTimeout) {
        DeferredResult<ResponseEntity> deferredResult = processGetDeviceByName(jwtToken, deviceName);

        if (deferredResult.hasResult()) {
            try {
                ResponseEntity<?> deviceResponse = (ResponseEntity<?>) deferredResult.getResult();
                if (deviceResponse.getStatusCode().is2xxSuccessful()) {
                    DeferredResult<ResponseEntity> result = new DeferredResult<>();
                    ResponseEntity<Device> deviceResponseEntity = (ResponseEntity<Device>) deviceResponse;
                    if (deviceResponseEntity.getBody() != null) {
                        Device device = deviceResponseEntity.getBody();

                        DeferredResult<ResponseEntity> getAttributeResponse = processGetAttributes(jwtToken, device.getUuidId().toString());
                        ResponseEntity<ArrayList<JsonNode>> getAttributeResponseEntity = (ResponseEntity<ArrayList<JsonNode>>) getAttributeResponse.getResult();

                        if (getAttributeResponseEntity.getStatusCode().is2xxSuccessful()) {
                            ArrayList<JsonNode> attributeResult = getAttributeResponseEntity.getBody();

                            String stream_key = "";
                            String playback_id = "";

                            for ( JsonNode atrributeInfo : attributeResult) {
                                switch (atrributeInfo.get("key").textValue()) {
                                    case "stream_key":
                                        stream_key = atrributeInfo.get("value").textValue();
                                        break;
                                    case "playback_id":
                                        playback_id = atrributeInfo.get("value").textValue();
                                        break;
                                }
                            }

                            if (!stream_key.isEmpty() && !playback_id.isEmpty()) {
                                String endPointURL = muxhttpClient.getBaseUrl() + "/video/v1/live-streams/" + stream_key;
                                HttpHeaders headers = getBasicAuthHttpHeaders(muxhttpClient.muxApiToken, muxhttpClient.muxApiSecret);
                                ObjectNode liveSteamRequest = mapper.createObjectNode();
                                HttpEntity<JsonNode> httpEntity = new HttpEntity<>(liveSteamRequest, headers);
                                log.debug("Try to process delete a live stream request: {}, requestTime: {}", endPointURL, System.currentTimeMillis());

                                ListenableFuture<ResponseEntity<JsonNode>> liveStreamFuture = muxhttpClient.exchange(
                                        endPointURL, HttpMethod.DELETE, httpEntity, JsonNode.class);

                                ResponseEntity<JsonNode> data = liveStreamFuture.get();

                                if (data.getStatusCode() == HttpStatus.NO_CONTENT) {
                                    ObjectNode responseNode = mapper.createObjectNode();

                                    ObjectNode dataResult = mapper.createObjectNode();
                                    dataResult.put("rpcId", device.getUuidId().toString());
                                    dataResult.put("status", "SUCCESS");
                                    responseNode.set("data", dataResult);

                                    result.setResult(new ResponseEntity<>(responseNode, HttpStatus.OK));
                                } else {
                                    result.setErrorResult(new ResponseEntity<>(data.getBody(), data.getStatusCode()));
                                }
                            } else {
                                result.setErrorResult(new ResponseEntity<>("There are no values", HttpStatus.NO_CONTENT));
                            }
                        } else {
                            result.setErrorResult(getAttributeResponseEntity);
                        }

                        return result;
                    }
                }

                return deferredResult;
            } catch (Exception e) {
                Throwable cause = e.getCause();
                DeferredResult<ResponseEntity> errorDeferredResult = new DeferredResult<>();
                errorDeferredResult.setErrorResult(onFailureHandler(cause, "processDeviceRestApiRequestToRuleEngine"));
                return errorDeferredResult;
            }
        } else {
            return deferredResult;
        }
    }

    @Override
    public DeferredResult<ResponseEntity> processGetLiveStreams(String jwtToken, JsonNode requestBody) {
        DeferredResult<ResponseEntity> result = new DeferredResult<>();

        String endPointURL = muxhttpClient.getBaseUrl() + "/video/v1/live-streams";
        HttpHeaders headers = getBasicAuthHttpHeaders(muxhttpClient.muxApiToken, muxhttpClient.muxApiSecret);
        ObjectNode liveSteamRequest = mapper.createObjectNode();
        HttpEntity<JsonNode> httpEntity = new HttpEntity<>(liveSteamRequest, headers);
        log.debug("Try to process get live streams request: {}, requestTime: {}", endPointURL, System.currentTimeMillis());
        ListenableFuture<ResponseEntity<JsonNode>> future = muxhttpClient.exchange(
                endPointURL, HttpMethod.GET, httpEntity, JsonNode.class);
        future.addCallback(new ListenableFutureCallback<>() {
            @Override
            public void onSuccess(ResponseEntity<JsonNode> responseEntity) {
//                log.info("Successfully processed login request for user: {}", tokenKey.getUserName());
//                jwtTokenCache.put(tokenKey, responseEntity.getBody());
                ObjectMapper objectMapper = new ObjectMapper();
                ArrayList<LiveStream> liveStreamList = new ArrayList<>();

                try {
                    String jsonArrayAsString = objectMapper.writeValueAsString(responseEntity.getBody().get("data"));
                    liveStreamList = objectMapper.readValue(jsonArrayAsString, new TypeReference<ArrayList<LiveStream>>() {});
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                }
                result.setResult(new ResponseEntity<>(liveStreamList, HttpStatus.OK));
            }

            @Override
            public void onFailure(Throwable throwable) {
                log.error("Failed to process request: {} due to: {}", endPointURL, throwable.getMessage());
                if (throwable instanceof HttpClientErrorException) {
                    result.setErrorResult(new ResponseEntity<>(throwable.getMessage(), ((HttpClientErrorException) throwable).getStatusCode()));
                } else {
                    result.setErrorResult(new ResponseEntity<>(throwable.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR));
                }
            }
        });

        return result;
    }

    private ResponseEntity onSuccessRpcCompletedResponseHandle(JsonNode body, Rpc rpc) {
        if (body != null && !body.isNull()) {
            if (!body.isEmpty()) {
                if (body.has("error")) {
                    return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
                } else if (body.has("response")) {
                    JsonNode responseBody = body.get("response");
                    return new ResponseEntity<>(responseBody, HttpStatus.OK);
                }
            }
        }
        return new ResponseEntity<>(defaultRpcResponse(rpc), HttpStatus.OK);
    }

    private ObjectNode defaultRpcResponse(Rpc rpc) {
        ObjectNode response = mapper.createObjectNode();
        response.put("rpcId", rpc.getId().toString());
        JsonNode request = rpc.getRequest();
        if (request.has("body")) {
            response.set("request", request.get("body"));
        } else {
            response.set("request", request);
        }
        response.set("response", rpc.getResponse());
        response.put("status", rpc.getStatus().name());
        return response;
    }

    private Device updateDeviceFields(Device device, DeviceEntity deviceEntity) {
        if (device.getId() != null) {
            if (!StringUtils.isEmpty(deviceEntity.getNewName())) {
                device.setName(deviceEntity.getNewName());
            }
        } else {
            device.setName(deviceEntity.getName());
        }
        device.setType(deviceEntity.getType());
        if (deviceEntity.getLabel() != null) {
            device.setLabel(deviceEntity.getLabel());
        }
        return device;
    }

    private DeferredResult<ResponseEntity> saveDevice(Device device, String accessToken, String jwtToken) {
        String saveDeviceEndPointURL = tbhttpClient.getBaseUrl() + "/api/device";
        if (device.getId() == null && !StringUtils.isEmpty(accessToken)) {
            saveDeviceEndPointURL += "?accessToken=" + accessToken;
        }
        DeferredResult<ResponseEntity> result = new DeferredResult<>();
        HttpHeaders headers = getDefaultHttpHeaders(jwtToken);
        HttpEntity<Device> httpEntity = new HttpEntity<>(device, headers);
        log.debug("Try to process save device request: {}, requestTime: {}", saveDeviceEndPointURL, System.currentTimeMillis());
        ListenableFuture<ResponseEntity<Device>> provisionDeviceFuture = tbhttpClient.exchange(
                saveDeviceEndPointURL, HttpMethod.POST, httpEntity, Device.class);
        String finalSaveDeviceEndPointURL = saveDeviceEndPointURL;
        provisionDeviceFuture.addCallback(new ListenableFutureCallback<>() {
            @Override
            public void onFailure(Throwable throwable) {
                result.setErrorResult(onFailureHandler(throwable, finalSaveDeviceEndPointURL));
            }

            @Override
            public void onSuccess(ResponseEntity<Device> deviceResponseEntity) {
                log.info("Successfully processed request: {}", finalSaveDeviceEndPointURL);
                Device savedDevice = deviceResponseEntity.getBody();
                ObjectNode responseNode = mapper.createObjectNode();
                responseNode.put("Name", savedDevice.getName());
                responseNode.put("Type", savedDevice.getType());
                responseNode.put("Label", savedDevice.getLabel());
                if (device.getId() == null) {
                    if (!StringUtils.isEmpty(accessToken)) {
                        responseNode.put("accessToken", accessToken);
                        result.setResult(new ResponseEntity<>(responseNode, HttpStatus.OK));
                    } else {
                        getDeviceCredentialsByDeviceId(savedDevice.getId(), jwtToken, responseNode, result, null);
                    }
                } else {
                    getDeviceCredentialsByDeviceId(savedDevice.getId(), jwtToken, responseNode, result, null);
                }
            }
        });
        return result;
    }

    private void getDeviceCredentialsByDeviceId(DeviceId deviceId, String jwtToken, ObjectNode responseNode, DeferredResult<ResponseEntity> result, JsonNode payload) {
        String getDeviceCredentialsEndpointUrl = tbhttpClient.getBaseUrl() + "/api/device/" + deviceId.getId() + "/credentials";
        HttpEntity<DeviceCredentials> deviceCredentialsHttpEntity = new HttpEntity<>(getDefaultHttpHeaders(jwtToken));
        log.debug("Try to process get device credentials request: {}, requestTime: {}", getDeviceCredentialsEndpointUrl, System.currentTimeMillis());
        ListenableFuture<ResponseEntity<DeviceCredentials>> deviceCredentialsFuture = tbhttpClient.exchange(
                getDeviceCredentialsEndpointUrl, HttpMethod.GET, deviceCredentialsHttpEntity, DeviceCredentials.class);
        deviceCredentialsFuture.addCallback(new ListenableFutureCallback<>() {

            @Override
            public void onFailure(Throwable throwable) {
                result.setErrorResult(onFailureHandler(throwable, getDeviceCredentialsEndpointUrl));
            }

            @Override
            public void onSuccess(ResponseEntity<DeviceCredentials> credentialsResponseEntity) {
                log.info("Successfully processed request: {}", getDeviceCredentialsEndpointUrl);
                String credentialsId = credentialsResponseEntity.getBody().getCredentialsId();
                if (responseNode != null) {
                    responseNode.put("accessToken", credentialsId);
                    result.setResult(new ResponseEntity<>(responseNode, HttpStatus.OK));
                } else {
                    String saveDeviceAttributesEndPointURL = tbhttpClient.getBaseUrl() + "/api/v1/" + credentialsId + "/attributes";
                    HttpHeaders headers = new HttpHeaders();
                    headers.add("Accept", "application/json");
                    HttpEntity<JsonNode> httpEntity = new HttpEntity<>(payload, headers);
                    log.debug("Try to process save device attributes request: {}, requestTime: {}", saveDeviceAttributesEndPointURL, System.currentTimeMillis());
                    ListenableFuture<ResponseEntity<Void>> saveDeviceClientAttributesFuture = tbhttpClient.exchange(
                            saveDeviceAttributesEndPointURL, HttpMethod.POST, httpEntity, Void.class);
                    saveDeviceClientAttributesFuture.addCallback(new ListenableFutureCallback<ResponseEntity<Void>>() {
                        @Override
                        public void onSuccess(ResponseEntity<Void> deviceResponseEntity) {
                            log.info("Successfully processed request: {}", saveDeviceAttributesEndPointURL);
                            result.setResult(new ResponseEntity(HttpStatus.OK));
                        }

                        @Override
                        public void onFailure(Throwable throwable) {
                            result.setErrorResult(onFailureHandler(throwable, saveDeviceAttributesEndPointURL));
                        }
                    });
                }
            }
        });
    }

    private ResponseEntity<String> onFailureHandler(Throwable throwable, String requestUrl) {
        log.error("Failed to process request: {} due to: {}", requestUrl, throwable.getMessage());
        if (throwable instanceof HttpClientErrorException) {
            String message = throwable.getMessage();
            return new ResponseEntity<>(message, ((HttpClientErrorException) throwable).getStatusCode());
        } else {
            return new ResponseEntity<>(throwable.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private ResponseEntity<String> deviceNotFoundResponseEntity(String deviceName) {
        return new ResponseEntity<>("Device with requested name: " + deviceName + " wasn't found!", HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<String> permissionDenied() {
        return new ResponseEntity<>("You don't have permission to perform this operation!", HttpStatus.FORBIDDEN);
    }

    private HttpHeaders getDefaultHttpHeaders(String jwtToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Authorization", jwtToken);
        headers.add("Accept", "application/json");
        return headers;
    }

    private HttpHeaders getBearerTokenHttpHeaders(String jwtToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken);
        headers.add(HttpHeaders.ACCEPT, "application/json");
        headers.add(HttpHeaders.CONTENT_TYPE, "application/json");
        return headers;
    }

    private HttpHeaders getBasicAuthHttpHeaders(String userName, String password) {
        String auth = userName + ":" + password;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, "Basic " + encodedAuth);
        headers.add(HttpHeaders.ACCEPT, "application/json");
        headers.add(HttpHeaders.CONTENT_TYPE, "application/json");
        return headers;
    }

    private HttpHeaders getS3TextHttpHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Accept", "text/plain");
        return headers;
    }

    private JsonNode getJwtTokenBodyBase64(String token) {
        try {
            byte[] jwtTokenDecoded = Base64.getDecoder().decode(token.split("\\.")[1]);
            return mapper.readTree(jwtTokenDecoded);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert jwtToken " + token + " due to: ", e);
        }
    }

}
