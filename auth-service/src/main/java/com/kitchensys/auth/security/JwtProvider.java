package com.kitchensys.auth.security;

import com.kitchensys.auth.domain.Employee;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;

/**
 * 09-security.md — Access Token(JWT, 1시간) 발급·검증. Refresh Token은 JWT가 아니라
 * {@link RefreshTokenService}가 Redis에 관리하는 난수 문자열이다(탈취 시 즉시 폐기 가능하도록).
 */
@Component
public class JwtProvider {

    private final SecretKey signingKey;
    private final long accessTokenTtlMinutes;

    public JwtProvider(
        @Value("${jwt.secret}") String secret,
        @Value("${jwt.access-token-ttl-minutes}") long accessTokenTtlMinutes
    ) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtlMinutes = accessTokenTtlMinutes;
    }

    public String issueAccessToken(Employee employee) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(employee.getId().toString())
            .claim("companyId", employee.getCompanyId().toString())
            .claim("role", employee.getRole().name())
            .claim("plan", employee.getCompany().getPlan().name())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(accessTokenTtlMinutes, ChronoUnit.MINUTES)))
            .signWith(signingKey)
            .compact();
    }

    public Optional<EmployeePrincipal> parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
            return Optional.of(new EmployeePrincipal(
                claims.getSubject(),
                claims.get("companyId", String.class),
                claims.get("role", String.class)
            ));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
