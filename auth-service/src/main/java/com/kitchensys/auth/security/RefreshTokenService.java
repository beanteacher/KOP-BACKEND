package com.kitchensys.auth.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 09-security.md — Refresh Token은 httpOnly 쿠키로만 오가는 난수 문자열이고, 서버는 Redis에
 * "token → employeeId"만 들고 있다(자체 서명 검증이 필요 없어 즉시 폐기가 가능하다).
 * per-employee 인덱스를 같이 관리해 "비밀번호 재설정 시 기존 모든 세션 무효화"를 지원한다.
 */
@Component
public class RefreshTokenService {

    private static final String TOKEN_KEY_PREFIX = "auth:refresh:";
    private static final String EMPLOYEE_INDEX_PREFIX = "auth:refresh:by-employee:";

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public RefreshTokenService(StringRedisTemplate redis, @Value("${jwt.refresh-token-ttl-days}") long ttlDays) {
        this.redis = redis;
        this.ttl = Duration.ofDays(ttlDays);
    }

    public String issue(UUID employeeId) {
        String token = UUID.randomUUID().toString() + UUID.randomUUID();
        redis.opsForValue().set(TOKEN_KEY_PREFIX + token, employeeId.toString(), ttl);
        String indexKey = EMPLOYEE_INDEX_PREFIX + employeeId;
        redis.opsForSet().add(indexKey, token);
        redis.expire(indexKey, ttl);
        return token;
    }

    public Optional<UUID> resolve(String token) {
        String employeeId = redis.opsForValue().get(TOKEN_KEY_PREFIX + token);
        return Optional.ofNullable(employeeId).map(UUID::fromString);
    }

    public void revoke(String token) {
        redis.delete(TOKEN_KEY_PREFIX + token);
    }

    /** 비밀번호 재설정·의심스러운 활동 대응 — 해당 직원의 모든 Refresh Token을 즉시 무효화한다. */
    public void revokeAllForEmployee(UUID employeeId) {
        String indexKey = EMPLOYEE_INDEX_PREFIX + employeeId;
        Set<String> tokens = redis.opsForSet().members(indexKey);
        if (tokens != null) {
            tokens.forEach(token -> redis.delete(TOKEN_KEY_PREFIX + token));
        }
        redis.delete(indexKey);
    }
}
