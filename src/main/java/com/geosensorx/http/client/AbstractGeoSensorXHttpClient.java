package com.geosensorx.http.client;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.client.AsyncRestTemplate;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.net.ssl.SSLException;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("deprecation")
public abstract class AbstractGeoSensorXHttpClient {

    private EventLoopGroup eventLoopGroup;
    private AsyncRestTemplate httpClient;

    @PostConstruct
    public void init() throws SSLException {
        eventLoopGroup = new NioEventLoopGroup();
        httpClient = initHttpClient(eventLoopGroup);
    }

    @PreDestroy
    public void preDestroy() {
        if (eventLoopGroup != null) {
            eventLoopGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS);
        }
    }

    protected abstract AsyncRestTemplate initHttpClient(EventLoopGroup eventLoopGroup) throws SSLException;

    protected  <T> ListenableFuture<ResponseEntity<T>> exchangeWithClassResponseType(String url, HttpMethod method, HttpEntity<?> httpEntity, Class<T> responseType) {
        return httpClient.exchange(url, method, httpEntity, responseType);
    }

    protected <T> ListenableFuture<ResponseEntity<T>> exchangeWithTypeReferenceResponseType(String url, HttpMethod method, HttpEntity<?> httpEntity, ParameterizedTypeReference<T> responseType) {
        return httpClient.exchange(url, method, httpEntity, responseType);
    }

}
