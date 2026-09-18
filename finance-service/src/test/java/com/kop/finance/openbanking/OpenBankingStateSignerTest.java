package com.kop.finance.openbanking;

import com.kop.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenBankingStateSignerTest {

    OpenBankingStateSigner signer = new OpenBankingStateSigner("local-dev-only-change-me-0123456789abcdef");

    @Test
    void issue한_state는_같은_companyId로_verify를_통과한다() {
        UUID companyId = UUID.randomUUID();
        String state = signer.issue(companyId);

        assertThatCode(() -> signer.verify(state, companyId)).doesNotThrowAnyException();
    }

    @Test
    void 다른_companyId로_verify하면_예외를_던진다() {
        String state = signer.issue(UUID.randomUUID());

        assertThatThrownBy(() -> signer.verify(state, UUID.randomUUID()))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "OPENBANKING_STATE_INVALID");
    }

    @Test
    void 위조된_state는_예외를_던진다() {
        UUID companyId = UUID.randomUUID();
        String state = signer.issue(companyId);
        String tampered = state.substring(0, state.length() - 1) + (state.endsWith("A") ? "B" : "A");

        assertThatThrownBy(() -> signer.verify(tampered, companyId))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "OPENBANKING_STATE_INVALID");
    }

    @Test
    void 형식이_이상한_state는_예외를_던진다() {
        assertThatThrownBy(() -> signer.verify("not-a-valid-state", UUID.randomUUID()))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "OPENBANKING_STATE_INVALID");
    }

    @Test
    void null_state는_예외를_던진다() {
        assertThatThrownBy(() -> signer.verify(null, UUID.randomUUID()))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "OPENBANKING_STATE_INVALID");
    }

    @Test
    void 만료된_state는_예외를_던진다() {
        UUID companyId = UUID.randomUUID();
        String expiredPayload = companyId + "|" + (java.time.Instant.now().getEpochSecond() - 10);
        String encoded = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(expiredPayload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String signature = (String) ReflectionTestUtils.invokeMethod(signer, "sign", expiredPayload);
        String expiredState = encoded + "." + signature;

        assertThatThrownBy(() -> signer.verify(expiredState, companyId))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "OPENBANKING_STATE_INVALID");
    }
}
