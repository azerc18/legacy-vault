package com.ltld.app.legacyvault.dev;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

import java.time.LocalDateTime;

/**
 * CHỈ Ở PROFILE dev: chain riêng, ưu tiên cao, chỉ khớp /dev/**, /api/dev/** và /api/executors/**,
 * để không đụng SecurityConfig chung (module Auth sẽ thay bằng JWT filter).
 */
@Configuration
@Profile("dev")
public class DevProfileSecurityConfig {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain devFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/dev/**", "/api/dev/**", "/api/executors/**")
                .csrf(csrf -> csrf.disable())
                .addFilterBefore(new DevHeaderAuthFilter(), AnonymousAuthenticationFilter.class)
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, e) -> {
                    // Contract: 401 trả ApiResponseError {success, message, timestamp}
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write("{\"success\":false,\"message\":\"Unauthorized\",\"timestamp\":\""
                            + LocalDateTime.now() + "\"}");
                }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/dev/**", "/api/dev/**").permitAll()
                        .anyRequest().authenticated());
        return http.build();
    }
}
