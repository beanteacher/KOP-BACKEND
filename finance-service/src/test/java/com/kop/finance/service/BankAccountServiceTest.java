package com.kop.finance.service;

import com.kop.common.crypto.AesEncryptor;
import com.kop.common.exception.BusinessException;
import com.kop.finance.domain.BankAccount;
import com.kop.finance.dto.BankAccountDto;
import com.kop.finance.openbanking.OpenBankingAccount;
import com.kop.finance.openbanking.OpenBankingClient;
import com.kop.finance.openbanking.OpenBankingStateSigner;
import com.kop.finance.openbanking.OpenBankingTokenResponse;
import com.kop.finance.repository.BankAccountRepository;
import com.kop.finance.security.EmployeePrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BankAccountServiceTest {

    @Mock BankAccountRepository bankAccountRepository;
    @Mock OpenBankingClient openBankingClient;
    @Mock OpenBankingStateSigner stateSigner;
    @Mock AesEncryptor aesEncryptor;

    BankAccountService bankAccountService;

    UUID companyId = UUID.randomUUID();
    EmployeePrincipal admin;
    EmployeePrincipal staff;

    @BeforeEach
    void setUp() {
        bankAccountService = new BankAccountService(bankAccountRepository, openBankingClient, stateSigner, aesEncryptor);
        admin = new EmployeePrincipal(UUID.randomUUID().toString(), companyId.toString(), "ADMIN", "FREE");
        staff = new EmployeePrincipal(UUID.randomUUID().toString(), companyId.toString(), "STAFF", "FREE");
    }

    private BankAccount existingAccount() {
        BankAccount account = BankAccount.register(
            companyId, "국민은행", "110-***-1234", "enc-fintech", "enc-access", "enc-refresh", Instant.now()
        );
        ReflectionTestUtils.setField(account, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(account, "createdAt", Instant.now());
        return account;
    }

    @Test
    void authorizeUrl_관리자가_호출하면_state를_포함한_URL을_반환한다() {
        when(stateSigner.issue(companyId)).thenReturn("signed-state");
        when(openBankingClient.buildAuthorizeUrl("signed-state")).thenReturn("https://testapi.openbanking.or.kr/oauth/2.0/authorize?state=signed-state");

        var response = bankAccountService.authorizeUrl(admin);

        assertThat(response.url()).contains("signed-state");
    }

    @Test
    void authorizeUrl_직원이_호출하면_FORBIDDEN을_던진다() {
        assertThatThrownBy(() -> bankAccountService.authorizeUrl(staff))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "FORBIDDEN");

        verifyNoInteractions(stateSigner, openBankingClient);
    }

    @Test
    void register_신규_계좌를_등록한다() {
        var request = new BankAccountDto.RegisterRequest("auth-code", "signed-state");
        doNothing().when(stateSigner).verify("signed-state", companyId);
        when(openBankingClient.exchangeToken("auth-code"))
            .thenReturn(new OpenBankingTokenResponse("access-tok", "refresh-tok", 7776000L, "1000000106", "login inquiry"));
        when(openBankingClient.fetchAccounts("1000000106", "access-tok"))
            .thenReturn(List.of(new OpenBankingAccount("fintech-use-num", "국민은행", "110-***-1234", "홍길동")));
        when(aesEncryptor.encrypt(anyString())).thenAnswer(inv -> "enc:" + inv.getArgument(0));
        when(bankAccountRepository.findByCompanyId(companyId)).thenReturn(Optional.empty());
        when(bankAccountRepository.saveAndFlush(any())).thenAnswer(inv -> {
            BankAccount account = inv.getArgument(0);
            ReflectionTestUtils.setField(account, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(account, "createdAt", Instant.now());
            return account;
        });

        var response = bankAccountService.register(admin, request);

        assertThat(response.connected()).isTrue();
        assertThat(response.bankName()).isEqualTo("국민은행");
        assertThat(response.accountNumberMasked()).isEqualTo("110-***-1234");
        verify(aesEncryptor).encrypt("fintech-use-num");
        verify(aesEncryptor).encrypt("access-tok");
        verify(aesEncryptor).encrypt("refresh-tok");
    }

    @Test
    void register_이미_연결된_계좌가_있으면_갱신한다() {
        BankAccount existing = existingAccount();
        var request = new BankAccountDto.RegisterRequest("auth-code", "signed-state");
        doNothing().when(stateSigner).verify("signed-state", companyId);
        when(openBankingClient.exchangeToken("auth-code"))
            .thenReturn(new OpenBankingTokenResponse("new-access", "new-refresh", 7776000L, "1000000106", "login inquiry"));
        when(openBankingClient.fetchAccounts("1000000106", "new-access"))
            .thenReturn(List.of(new OpenBankingAccount("new-fintech-use-num", "신한은행", "220-***-5678", "홍길동")));
        when(aesEncryptor.encrypt(anyString())).thenAnswer(inv -> "enc:" + inv.getArgument(0));
        when(bankAccountRepository.findByCompanyId(companyId)).thenReturn(Optional.of(existing));
        when(bankAccountRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = bankAccountService.register(admin, request);

        assertThat(response.bankName()).isEqualTo("신한은행");
        assertThat(response.accountNumberMasked()).isEqualTo("220-***-5678");
        assertThat(existing.getBankName()).isEqualTo("신한은행"); // 새 엔티티가 아니라 기존 엔티티가 갱신됨
    }

    @Test
    void register_연결할_계좌가_없으면_OPENBANKING_NO_ACCOUNT를_던진다() {
        var request = new BankAccountDto.RegisterRequest("auth-code", "signed-state");
        doNothing().when(stateSigner).verify("signed-state", companyId);
        when(openBankingClient.exchangeToken("auth-code"))
            .thenReturn(new OpenBankingTokenResponse("access-tok", "refresh-tok", 7776000L, "1000000106", "login"));
        when(openBankingClient.fetchAccounts("1000000106", "access-tok")).thenReturn(List.of());

        assertThatThrownBy(() -> bankAccountService.register(admin, request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "OPENBANKING_NO_ACCOUNT");

        verify(bankAccountRepository, never()).saveAndFlush(any());
    }

    @Test
    void register_직원이_호출하면_FORBIDDEN을_던진다() {
        var request = new BankAccountDto.RegisterRequest("auth-code", "signed-state");

        assertThatThrownBy(() -> bankAccountService.register(staff, request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "FORBIDDEN");

        verifyNoInteractions(stateSigner, openBankingClient, bankAccountRepository);
    }

    @Test
    void getStatus_연결된_계좌가_있으면_정보를_반환한다() {
        BankAccount existing = existingAccount();
        when(bankAccountRepository.findByCompanyId(companyId)).thenReturn(Optional.of(existing));

        var response = bankAccountService.getStatus(admin);

        assertThat(response.connected()).isTrue();
        assertThat(response.bankName()).isEqualTo("국민은행");
    }

    @Test
    void getStatus_연결된_계좌가_없으면_connected_false를_반환한다() {
        when(bankAccountRepository.findByCompanyId(companyId)).thenReturn(Optional.empty());

        var response = bankAccountService.getStatus(admin);

        assertThat(response.connected()).isFalse();
        assertThat(response.bankName()).isNull();
    }

    @Test
    void getStatus_직원이_호출하면_FORBIDDEN을_던진다() {
        assertThatThrownBy(() -> bankAccountService.getStatus(staff))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "FORBIDDEN");
    }
}
