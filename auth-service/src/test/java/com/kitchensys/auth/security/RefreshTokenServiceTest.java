package com.kitchensys.auth.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOps;

    RefreshTokenService service;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(redis, 30);
    }

    @Test
    void resolve_remember가_포함된_값은_그대로_파싱한다() {
        UUID employeeId = UUID.randomUUID();
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("auth:refresh:token-1")).thenReturn(employeeId + "|true");

        Optional<RefreshTokenService.ResolvedRefreshToken> result = service.resolve("token-1");

        assertThat(result).isPresent();
        assertThat(result.get().employeeId()).isEqualTo(employeeId);
        assertThat(result.get().remember()).isTrue();
    }

    /** rememberMe 필드를 추가하기 전에 발급된 토큰은 employeeId만 저장돼 있다 — 500 없이 remember=true로 취급해야 한다. */
    @Test
    void resolve_remember_필드가_없는_구버전_값은_remember_true로_취급한다() {
        UUID employeeId = UUID.randomUUID();
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("auth:refresh:token-legacy")).thenReturn(employeeId.toString());

        Optional<RefreshTokenService.ResolvedRefreshToken> result = service.resolve("token-legacy");

        assertThat(result).isPresent();
        assertThat(result.get().employeeId()).isEqualTo(employeeId);
        assertThat(result.get().remember()).isTrue();
    }

    @Test
    void resolve_존재하지_않는_토큰은_빈_값을_반환한다() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("auth:refresh:missing")).thenReturn(null);

        assertThat(service.resolve("missing")).isEmpty();
    }
}
