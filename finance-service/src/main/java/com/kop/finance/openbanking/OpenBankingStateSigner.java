package com.kop.finance.openbanking;

import com.kop.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 오픈뱅킹 인증 URL의 state 파라미터 — OAuth CSRF 방지용.
 *
 * 처음엔 "companyId + 만료시각"을 HMAC 서명해 서버 저장 없이 자기검증하는 방식으로 짰는데,
 * 실제 테스트베드에 붙여보니 state는 "32-byte fixed random string"이어야 한다(공식 API
 * 명세서) — 서명이 포함된 긴 문자열을 보내면 O0001/3000103(필수 파라미터 값이 존재하지
 * 않을 때/특정 파라미터 값이 중복으로 들어왔을 때)로 거부당함을 실제로 재현 확인했다.
 * 그래서 state는 32자 랜덤 문자열(UUID에서 하이픈만 제거)로 만들고, companyId·만료시각은
 * 서버 메모리에 따로 들고 있는 방식으로 바꿨다. 1회용(검증 시 즉시 제거)이라 재사용도 막는다.
 *
 * 한계: 인스턴스 로컬 메모리라 재시작하면 미완료 인증 요청은 전부 무효화되고, 여러 인스턴스로
 * 수평 확장하면(현재는 단일 인스턴스 로컬 개발) 요청을 처리한 인스턴스가 아닌 다른 인스턴스가
 * 콜백을 받으면 실패한다 — 그땐 Redis 같은 공유 저장소로 옮겨야 한다.
 */
@Component
public class OpenBankingStateSigner {

    private static final long TTL_SECONDS = 600; // 10분 — 은행 인증창에서 로그인하기 충분한 시간

    private final Map<String, PendingState> pending = new ConcurrentHashMap<>();

    public String issue(UUID companyId) {
        cleanupExpired();
        String state = UUID.randomUUID().toString().replace("-", ""); // 32자 고정
        pending.put(state, new PendingState(companyId, Instant.now().getEpochSecond() + TTL_SECONDS));
        return state;
    }

    /** companyId 일치·만료를 검증한다. 검증에 성공하든 실패하든 해당 state는 1회용이라 즉시 제거한다. */
    public void verify(String state, UUID expectedCompanyId) {
        PendingState found = state == null ? null : pending.remove(state);
        if (found == null || !found.companyId().equals(expectedCompanyId) || Instant.now().getEpochSecond() > found.expiresAt()) {
            throw invalid();
        }
    }

    private void cleanupExpired() {
        long now = Instant.now().getEpochSecond();
        pending.entrySet().removeIf(entry -> entry.getValue().expiresAt() < now);
    }

    private BusinessException invalid() {
        return new BusinessException(HttpStatus.BAD_REQUEST, "OPENBANKING_STATE_INVALID", "인증 요청이 유효하지 않거나 만료됐습니다. 다시 시도해주세요.");
    }

    private record PendingState(UUID companyId, long expiresAt) {}
}
