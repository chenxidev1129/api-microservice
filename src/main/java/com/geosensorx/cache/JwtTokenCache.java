package com.geosensorx.cache;


import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.concurrent.CompletableFuture;

public class JwtTokenCache {

    private AsyncLoadingCache<JwtTokenCacheKey, JsonNode> cache;

    public JwtTokenCache() {
        this.cache = Caffeine.newBuilder()
                .buildAsync(key -> {
                    throw new IllegalStateException("'get' methods calls are not supported!");
                });
    }

    public CompletableFuture<JsonNode> getIfPresent(JwtTokenCacheKey key) {
        return cache.getIfPresent(key);
    }

    public void put(JwtTokenCacheKey key, JsonNode value) {
        cache.put(key, CompletableFuture.completedFuture(value));
    }
}
