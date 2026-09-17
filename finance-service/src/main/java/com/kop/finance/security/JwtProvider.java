package com.kop.finance.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * 09-security.md — Access Token(JWT) 검증 전용. auth-service가 발급한 토큰을 여기서는
 * 서명·만료만 검증하고 claim을 읽는다(발급은 auth-service만 한다).
 */
@Component
public class JwtProvider {

    private final SecretKey signingKey;

    public JwtProvider(@Value("${jwt.secret}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public Optional<EmployeePrincipal> parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
            return Optional.of(new EmployeePrincipal(
                claims.getSubject(),
                claims.get("companyId", String.class),
                claims.get("role", String.class),
                claims.get("plan", String.class)
            ));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
