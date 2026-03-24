package com.smart.office.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * JWT 令牌生成与解析工具。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JwtTokenProvider {

    @Value("${app.auth.jwt.secret}")
    private String secret;

    @Value("${app.auth.jwt.expire-seconds:7200}")
    private long expireSeconds;

    @Value("${app.auth.jwt.issuer:smart-office}")
    private String issuer;

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成包含用户信息的 JWT。
     */
    public String generateToken(LoginUserDetails userDetails) {
        long now = System.currentTimeMillis();
        Date issuedAt = new Date(now);
        Date expireAt = new Date(now + expireSeconds * 1000);

        return Jwts.builder()
                .setSubject(userDetails.getUsername())
                .setIssuer(issuer)
                .setIssuedAt(issuedAt)
                .setExpiration(expireAt)
                .claim("id", userDetails.getId())
                .claim("userId", userDetails.getUserId())
                .claim("department", userDetails.getDepartment())
                .claim("groupCode", userDetails.getGroupCode())
                .claim("role", userDetails.getRole())
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 校验 Token 是否有效。
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT 已过期: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("JWT 校验失败: {}", e.getMessage());
        }
        return false;
    }

    /**
     * 获取 Token 中的用户名。
     */
    public String getUsernameFromToken(String token) {
        Claims claims = parseClaims(token);
        return claims.getSubject();
    }

    /**
     * 获取 Token 中记录的业务用户 ID。
     */
    public String getUserIdFromToken(String token) {
        Claims claims = parseClaims(token);
        Object userId = claims.get("userId");
        return userId != null ? userId.toString() : null;
    }

    /**
     * 返回 Token 的有效期（秒）。
     */
    public long getExpireSeconds() {
        return expireSeconds;
    }

    private Claims parseClaims(String token) {
        if (!StringUtils.hasText(token)) {
            throw new IllegalArgumentException("Token 不能为空");
        }
        return Jwts.parser()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
