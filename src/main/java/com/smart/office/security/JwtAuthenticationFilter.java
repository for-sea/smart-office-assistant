package com.smart.office.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT 认证过滤器，从请求头读取 Token 并注入 SecurityContext。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Value("${app.auth.jwt.header:Authorization}")
    private String headerName;

    @Value("${app.auth.jwt.token-prefix:Bearer }")
    private String tokenPrefix;

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService customUserDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = resolveToken(request);
            if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
                String username = jwtTokenProvider.getUsernameFromToken(token);
                if (StringUtils.hasText(username) &&
                        SecurityContextHolder.getContext().getAuthentication() == null) {
                    UserDetails userDetails =
                            customUserDetailsService.loadUserByUsername(username);
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (Exception e) {
            log.warn("JWT 过滤器处理异常: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String headerValue = request.getHeader(headerName);
        if (StringUtils.hasText(headerValue) && headerValue.startsWith(tokenPrefix)) {
            return headerValue.substring(tokenPrefix.length());
        }
        return null;
    }
}
