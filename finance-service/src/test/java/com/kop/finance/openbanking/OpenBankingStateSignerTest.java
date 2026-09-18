package com.kop.finance.openbanking;

import com.kop.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenBankingStateSignerTest {

    OpenBankingStateSigner signer = new OpenBankingStateSigner();

    @Test
    void issue한_state는_32자다() {
        // 오픈뱅킹 API 명세서 — state는 "32-byte fixed random string"이어야 한다.
        String state = signer.issue(UUID.randomUUID());

        assertThat(state).hasSize(32);
    }

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
    void 같은_state를_두_번_verify하면_두_번째는_예외를_던진다() {
        // 1회용 — code와 마찬가지로 재사용을 막는다.
        UUID companyId = UUID.randomUUID();
        String state = signer.issue(companyId);
        signer.verify(state, companyId);

        assertThatThrownBy(() -> signer.verify(state, companyId))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "OPENBANKING_STATE_INVALID");
    }

    @Test
    void 발급한_적_없는_state는_예외를_던진다() {
        assertThatThrownBy(() -> signer.verify("never-issued-state-1234567890ab", UUID.randomUUID()))
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
    @SuppressWarnings("unchecked")
    void 만료된_state는_예외를_던진다() throws Exception {
        UUID companyId = UUID.randomUUID();
        String state = signer.issue(companyId);

        // PendingState는 record라 필드가 final이라 reflection으로 값을 바꿔치기할 수 없다 —
        // 만료된 값을 담은 인스턴스를 새로 만들어 맵 항목을 통째로 교체한다.
        Class<?> pendingStateClass = Class.forName(OpenBankingStateSigner.class.getName() + "$PendingState");
        var constructor = pendingStateClass.getDeclaredConstructor(UUID.class, long.class);
        constructor.setAccessible(true);
        Object expiredEntry = constructor.newInstance(companyId, Instant.now().getEpochSecond() - 10);

        Map<String, Object> pending = (Map<String, Object>) ReflectionTestUtils.getField(signer, "pending");
        pending.put(state, expiredEntry);

        assertThatThrownBy(() -> signer.verify(state, companyId))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "OPENBANKING_STATE_INVALID");
    }
}
