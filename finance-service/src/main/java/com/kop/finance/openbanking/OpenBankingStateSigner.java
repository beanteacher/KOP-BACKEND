package com.kop.finance.openbanking;

import com.kop.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * 오픈뱅킹 인증 URL의 state 파라미터 — OAuth CSRF 방지용. 서버에 세션/DB로 들고 있지 않고
 * "companyId + 만료시각"을 HMAC-SHA256으로 서명해 자기 검증(self-verifying)한다 — 별도
 * 저장소 없이도 위조·재사용(다른 회사로 바꿔치기)·유효기간 만료를 전부 검증할 수 있다.
 */
@Component
public class OpenBankingStateSigner {

    private static final long TTL_SECONDS = 600; // 10분 — 은행 인증창에서 로그인하기 충분한 시간

    private final SecretKeySpec key;

    public OpenBankingStateSigner(@Value("${app.encryption.secret:local-dev-only-change-me-0123456789abcdef}") String secret) {
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    public String issue(UUID companyId) {
        long expiresAt = Instant.now().getEpochSecond() + TTL_SECONDS;
        String payload = companyId + "|" + expiresAt;
        return encode(payload) + "." + sign(payload);
    }

    /** 서명·만료·companyId 일치를 검증한다. 하나라도 어긋나면 예외를 던진다. */
    public void verify(String state, UUID expectedCompanyId) {
        String[] parts = state == null ? new String[0] : state.split("\\.", 2);
        if (parts.length != 2) {
            throw invalid();
        }

        String payload = decode(parts[0]);
        if (!sign(payload).equals(parts[1])) {
            throw invalid();
        }

        String[] fields = payload.split("\\|", 2);
        UUID companyId;
        long expiresAt;
        try {
            companyId = UUID.fromString(fields[0]);
            expiresAt = Long.parseLong(fields[1]);
        } catch (RuntimeException e) {
            throw invalid();
        }

        if (!companyId.equals(expectedCompanyId) || Instant.now().getEpochSecond() > expiresAt) {
            throw invalid();
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private BusinessException invalid() {
        return new BusinessException(HttpStatus.BAD_REQUEST, "OPENBANKING_STATE_INVALID", "인증 요청이 유효하지 않거나 만료됐습니다. 다시 시도해주세요.");
    }
}
