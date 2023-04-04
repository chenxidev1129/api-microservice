package com.geosensorx.http.client;

import io.netty.channel.EventLoopGroup;
import io.netty.handler.ssl.SslContextBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.Netty4ClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.client.AsyncRestTemplate;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.net.ssl.SSLException;

@Service
@Slf4j
@SuppressWarnings("deprecation")
public class MuxGeoSensorXHttpClient extends AbstractGeoSensorXHttpClient implements GeoSensorXHttpClient  {

    @Value("${rest.mux.url}")
    String baseUrl;

    @Value("${rest.mux.ssl}")
    private boolean ssl;

    @Value("${rest.mux.read_timeout}")
    private int readTimeout;

    @Value("${rest.mux.token}")
    public String muxApiToken;

    @Value("${rest.mux.secret}")
    public String muxApiSecret;

    @PostConstruct
    public void init() throws SSLException {
        super.init();
    }

    @PreDestroy
    public void preDestroy() {
        super.preDestroy();
    }

    @Override
    protected AsyncRestTemplate initHttpClient(EventLoopGroup eventLoopGroup) throws SSLException {
        Netty4ClientHttpRequestFactory nettyFactory = new Netty4ClientHttpRequestFactory(eventLoopGroup);
        if (ssl) {
            nettyFactory.setSslContext(SslContextBuilder.forClient().build());
            nettyFactory.setReadTimeout(readTimeout);
        } else {
            if (baseUrl.startsWith("https")) {
                throw new RuntimeException("Failed to init HTTP Client for S3 microservice! rest.s3.url should use http protocol when ssl is set to false!");
            }
        }
        return new AsyncRestTemplate(nettyFactory);
    }

    @Override
    public <T> ListenableFuture<ResponseEntity<T>> exchange(String url, HttpMethod method, HttpEntity<?> httpEntity, Class<T> responseType) {
        return exchangeWithClassResponseType(url, method, httpEntity, responseType);
    }

    @Override
    public <T> ListenableFuture<ResponseEntity<T>> exchange(String url, HttpMethod method, HttpEntity<?> httpEntity, ParameterizedTypeReference<T> responseType) {
        return exchangeWithTypeReferenceResponseType(url, method, httpEntity, responseType);
    }

    @Override
    public String getBaseUrl() {
        return baseUrl;
    }
}