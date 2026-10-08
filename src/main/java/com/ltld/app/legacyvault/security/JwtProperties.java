package com.ltld.app.legacyvault.security;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Instant;

@Validated
@ConfigurationProperties(prefix = "security.jwt")
@Data
public class JwtProperties {

    @NotBlank(message = "JWT secret must not be blank")
    private String secret;

    private long accessTokenTtlSeconds;
    private long refreshTokenTtlSeconds;
    boolean cookieSecure;
}
