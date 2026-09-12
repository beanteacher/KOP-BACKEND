package com.kitchensys.gateway;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 04-system-architecture.md "API Gateway — 인증 검증" — Access Token 서명·만료만 여기서 빠르게
 * 걸러낸다. 역할(관리자/직원) 권한은 여기서 판단하지 않는다 — 그건 각 서비스가
 * 09-security.md 인가 절에 따라 자기 엔드포인트에서 @PreAuthorize로 강제한다.
 *
 * 06-api-design.md "인증 불필요 엔드포인트" 목록과 반드시 같이 맞춘다 — 한쪽만 바꾸면
 * 여기서 막히거나, 반대로 검증 없이 통과하는 엔드포인트가 생긴다.
 */
@Component
public class JwtGatewayFilter implements GlobalFilter, Ordered {

    private static final List<String> PUBLIC_PATHS = List.of(
        "/api/auth/register",
        "/api/auth/verify-email",
        "/api/auth/login",
        "/api/auth/refresh",
        "/api/auth/password-reset/request",
        "/api/auth/password-reset/confirm"
    );

    private final SecretKey signingKey;

    public JwtGatewayFilter(@Value("${jwt.secret}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (isPublic(path)) {
            return chain.filter(exchange);
        }

        String header = request.getHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return unauthorized(exchange);
        }

        try {
            Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(header.substring(7));
            return chain.filter(exchange);
        } catch (JwtException e) {
            return unauthorized(exchange);
        }
    }

    private boolean isPublic(String path) {
        if (PUBLIC_PATHS.contains(path)) return true;
        if (path.startsWith("/api/auth/invitations/") && path.endsWith("/accept")) return true;
        return path.startsWith("/api/drawings/shared/");
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8));
        byte[] body = "{\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"토큰이 없거나 만료되었습니다\"}}"
            .getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(
            Mono.just(exchange.getResponse().bufferFactory().wrap(body))
        );
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
