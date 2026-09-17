package com.kop.auth.security;

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
    private static final String VALUE_DELIMITER = "|";

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public RefreshTokenService(StringRedisTemplate redis, @Value("${jwt.refresh-token-ttl-days}") long ttlDays) {
        this.redis = redis;
        this.ttl = Duration.ofDays(ttlDays);
    }

    /**
     * remember는 로그인 폼의 "로그인 상태 유지" 체크 여부 — 값 자체는 여기서 쓰지 않고, 발급받은
     * 토큰과 함께 저장해뒀다가 /refresh 때 그대로 돌려줘서(resolve) 컨트롤러가 재발급 쿠키의
     * Max-Age(지속 쿠키 vs 세션 쿠키)를 최초 로그인과 동일하게 유지하도록 한다.
     */
    public String issue(UUID employeeId, boolean remember) {
        String token = UUID.randomUUID().toString() + UUID.randomUUID();
        redis.opsForValue().set(TOKEN_KEY_PREFIX + token, employeeId + VALUE_DELIMITER + remember, ttl);
        String indexKey = EMPLOYEE_INDEX_PREFIX + employeeId;
        redis.opsForSet().add(indexKey, token);
        redis.expire(indexKey, ttl);
        return token;
    }

    /**
     * 이 필드를 추가하기 전에 발급된(배포 직전까지 Redis에 남아있는) 값은 remember 없이
     * employeeId만 저장돼 있다 — split 결과가 1개뿐이면 그 구버전 토큰으로 보고, 기존 동작
     * (항상 30일 지속 쿠키)과 같도록 remember=true로 취급한다. 없으면 재발급 때마다 500이 난다.
     */
    public Optional<ResolvedRefreshToken> resolve(String token) {
        String value = redis.opsForValue().get(TOKEN_KEY_PREFIX + token);
        if (value == null) {
            return Optional.empty();
        }
        String[] parts = value.split("\\" + VALUE_DELIMITER, 2);
        boolean remember = parts.length < 2 || Boolean.parseBoolean(parts[1]);
        return Optional.of(new ResolvedRefreshToken(UUID.fromString(parts[0]), remember));
    }

    public record ResolvedRefreshToken(UUID employeeId, boolean remember) {}

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
