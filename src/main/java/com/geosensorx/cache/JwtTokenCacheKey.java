package com.geosensorx.cache;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class JwtTokenCacheKey {

    private String userName;
    private String password;

}
