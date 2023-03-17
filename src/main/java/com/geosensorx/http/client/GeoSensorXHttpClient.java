package com.geosensorx.http.client;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.concurrent.ListenableFuture;

public interface GeoSensorXHttpClient {

    <T> ListenableFuture<ResponseEntity<T>> exchange(String url, HttpMethod method, HttpEntity<?> httpEntity, Class<T> responseType);

    <T> ListenableFuture<ResponseEntity<T>> exchange(String url, HttpMethod method, HttpEntity<?> httpEntity, ParameterizedTypeReference<T> responseType);

    String getBaseUrl();

}
