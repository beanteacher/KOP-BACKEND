package com.kop.auth.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * 1회용 토큰 공용 컴포넌트 — 이메일 인증 토큰, 비밀번호 재설정 토큰이 같은 모양(난수 →
 * Redis에 주체 id 저장, 유효기간, 1회 사용 후 폐기)이라 하나로 합쳤다. purpose로 네임스페이스만
 * 분리한다(예: "verify-email", "password-reset").
 */
@Component
public class OneTimeTokenService {

    private static final String KEY_PREFIX = "auth:one-time-token:";

    private final StringRedisTemplate redis;

    public OneTimeTokenService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public String issue(String purpose, UUID subjectId, Duration ttl) {
        String token = UUID.randomUUID().toString();
        redis.opsForValue().set(key(purpose, token), subjectId.toString(), ttl);
        return token;
    }

    /** 조회와 동시에 폐기한다 — 재사용 방지(09-security.md). */
    public Optional<UUID> consume(String purpose, String token) {
        String key = key(purpose, token);
        String subjectId = redis.opsForValue().get(key);
        if (subjectId == null) {
            return Optional.empty();
        }
        redis.delete(key);
        return Optional.of(UUID.fromString(subjectId));
    }

    private String key(String purpose, String token) {
        return KEY_PREFIX + purpose + ":" + token;
    }
}
