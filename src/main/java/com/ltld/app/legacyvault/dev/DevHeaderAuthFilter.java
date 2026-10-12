package com.ltld.app.legacyvault.dev;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/** CHỈ DÙNG Ở PROFILE dev: coi header X-Dev-User (email) là người đã đăng nhập, thay cho JWT chưa có. */
public class DevHeaderAuthFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Dev-User";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String email = request.getHeader(HEADER);
        if (email != null && !email.isBlank() && SecurityContextHolder.getContext().getAuthentication() == null) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    email.trim(), null, List.of(new SimpleGrantedAuthority("ROLE_EXECUTOR"))));
        }
        chain.doFilter(request, response);
    }
}
