package com.kop.finance.gateway;

import com.kop.common.crypto.AesEncryptor;
import com.kop.finance.domain.BankAccount;
import com.kop.finance.openbanking.OpenBankingClient;
import com.kop.finance.openbanking.OpenBankingTokenResponse;
import com.kop.finance.repository.BankAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RealBankTransactionGatewayTest {

    @Mock OpenBankingClient openBankingClient;
    @Mock BankAccountRepository bankAccountRepository;
    @Mock AesEncryptor aesEncryptor;

    RealBankTransactionGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new RealBankTransactionGateway(openBankingClient, bankAccountRepository, aesEncryptor);
    }

    private BankAccount accountWithExpiry(Instant tokenExpiresAt) {
        BankAccount account = BankAccount.register(
            UUID.randomUUID(), "국민은행", "110-***-1234", "enc-fintech", "enc-access", "enc-refresh", tokenExpiresAt
        );
        ReflectionTestUtils.setField(account, "id", UUID.randomUUID());
        return account;
    }

    @Test
    void 토큰이_유효하면_갱신_없이_바로_조회한다() {
        BankAccount account = accountWithExpiry(Instant.now().plusSeconds(3600));
        when(aesEncryptor.decrypt("enc-access")).thenReturn("access-tok");
        when(aesEncryptor.decrypt("enc-fintech")).thenReturn("fintech-use-num");
        when(openBankingClient.fetchTransactionHistory(eq("fintech-use-num"), eq("access-tok"), any(), any()))
            .thenReturn(List.of());

        gateway.fetchTransactions(account, LocalDate.now().minusDays(7), LocalDate.now());

        verify(openBankingClient, never()).refreshToken(anyString());
        verify(bankAccountRepository, never()).save(any());
    }

    @Test
    void 토큰이_만료_임박이면_먼저_갱신하고_새_토큰으로_조회한다() {
        BankAccount account = accountWithExpiry(Instant.now().plusSeconds(60)); // 5분 버퍼 이내
        when(aesEncryptor.decrypt("enc-refresh")).thenReturn("refresh-tok");
        when(aesEncryptor.decrypt("enc-fintech")).thenReturn("fintech-use-num");
        when(aesEncryptor.encrypt("new-access-tok")).thenReturn("enc-new-access");
        when(openBankingClient.refreshToken("refresh-tok"))
            .thenReturn(new OpenBankingTokenResponse("new-access-tok", null, 7776000L, "1000000106", "login inquiry"));
        when(openBankingClient.fetchTransactionHistory(eq("fintech-use-num"), eq("new-access-tok"), any(), any()))
            .thenReturn(List.of());

        gateway.fetchTransactions(account, LocalDate.now().minusDays(7), LocalDate.now());

        verify(bankAccountRepository).save(account);
        assertThat(account.getAccessToken()).isEqualTo("enc-new-access");
        assertThat(account.getRefreshToken()).isEqualTo("enc-refresh"); // 갱신 응답엔 새 refresh_token이 없다 — 기존 값 유지
        verify(openBankingClient).fetchTransactionHistory(eq("fintech-use-num"), eq("new-access-tok"), any(), any());
    }

    @Test
    void 조회_결과를_그대로_반환한다() {
        BankAccount account = accountWithExpiry(Instant.now().plusSeconds(3600));
        when(aesEncryptor.decrypt("enc-access")).thenReturn("access-tok");
        when(aesEncryptor.decrypt("enc-fintech")).thenReturn("fintech-use-num");
        BankTransaction transaction = new BankTransaction(
            java.time.LocalDateTime.now(), BankTransaction.TransactionType.DEPOSIT, 85_000L, "한성식자재", 1_000_000L
        );
        when(openBankingClient.fetchTransactionHistory(eq("fintech-use-num"), eq("access-tok"), any(), any()))
            .thenReturn(List.of(transaction));

        List<BankTransaction> result = gateway.fetchTransactions(account, LocalDate.now().minusDays(7), LocalDate.now());

        assertThat(result).containsExactly(transaction);
    }
}
