package com.kop.finance.gateway;

import com.kop.common.crypto.AesEncryptor;
import com.kop.finance.domain.BankAccount;
import com.kop.finance.openbanking.OpenBankingClient;
import com.kop.finance.openbanking.OpenBankingTokenResponse;
import com.kop.finance.repository.BankAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * {@link BankTransactionGateway} 실제 구현체 — openbanking.gateway-provider=real일 때만
 * 활성화된다(기본은 {@link MockBankTransactionGateway}). access_token이 만료(또는 임박)면
 * 조회 전에 refresh_token으로 먼저 갱신하고 새 토큰을 저장한다 — 호출자는 토큰 상태를 몰라도 된다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openbanking.gateway-provider", havingValue = "real")
public class RealBankTransactionGateway implements BankTransactionGateway {

    private static final long REFRESH_BUFFER_SECONDS = 300; // 만료 5분 전이면 미리 갱신

    private final OpenBankingClient openBankingClient;
    private final BankAccountRepository bankAccountRepository;
    private final AesEncryptor aesEncryptor;

    @Override
    public List<BankTransaction> fetchTransactions(BankAccount account, LocalDate from, LocalDate to) {
        String accessToken = ensureFreshAccessToken(account);
        String fintechUseNum = aesEncryptor.decrypt(account.getFintechUseNum());
        return openBankingClient.fetchTransactionHistory(fintechUseNum, accessToken, from, to);
    }

    private String ensureFreshAccessToken(BankAccount account) {
        if (account.getTokenExpiresAt().isAfter(Instant.now().plusSeconds(REFRESH_BUFFER_SECONDS))) {
            return aesEncryptor.decrypt(account.getAccessToken());
        }

        String refreshToken = aesEncryptor.decrypt(account.getRefreshToken());
        OpenBankingTokenResponse refreshed = openBankingClient.refreshToken(refreshToken);
        Instant newExpiresAt = Instant.now().plusSeconds(refreshed.expiresIn());

        // 갱신 응답엔 새 refresh_token이 없다 — 기존 암호문을 그대로 유지한다.
        account.updateTokens(aesEncryptor.encrypt(refreshed.accessToken()), account.getRefreshToken(), newExpiresAt);
        bankAccountRepository.save(account);
        return refreshed.accessToken();
    }
}
