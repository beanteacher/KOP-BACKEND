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
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 관리자 전용 — 회사의 오픈뱅킹 연동 계좌를 연결한다(회사당 1개). 거래내역 조회는 여기 없다 —
 * 연결된 계좌로 실제 거래내역을 가져오는 건 BankTransactionGateway의 실제 구현체(별도 작업) 몫이다.
 */
@Service
@RequiredArgsConstructor
public class BankAccountService {

    private final BankAccountRepository bankAccountRepository;
    private final OpenBankingClient openBankingClient;
    private final OpenBankingStateSigner stateSigner;
    private final AesEncryptor aesEncryptor;

    @Transactional(readOnly = true)
    public BankAccountDto.AuthorizeUrlResponse authorizeUrl(EmployeePrincipal principal) {
        requireAdmin(principal);
        UUID companyId = UUID.fromString(principal.companyId());
        String state = stateSigner.issue(companyId);
        return new BankAccountDto.AuthorizeUrlResponse(openBankingClient.buildAuthorizeUrl(state));
    }

    @Transactional
    public BankAccountDto.Response register(EmployeePrincipal principal, BankAccountDto.RegisterRequest request) {
        requireAdmin(principal);
        UUID companyId = UUID.fromString(principal.companyId());
        stateSigner.verify(request.state(), companyId);

        OpenBankingTokenResponse token = openBankingClient.exchangeToken(request.code());
        List<OpenBankingAccount> accounts = openBankingClient.fetchAccounts(token.userSeqNo(), token.accessToken());
        if (accounts.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "OPENBANKING_NO_ACCOUNT", "연결할 계좌가 없습니다. 인증 시 최소 1개 계좌를 선택해주세요.");
        }
        // 회사당 계좌 1개(company_id UNIQUE) — 인증 과정에서 여러 계좌를 선택했다면 첫 번째만 쓴다.
        OpenBankingAccount account = accounts.get(0);

        String encryptedFintechUseNum = aesEncryptor.encrypt(account.fintechUseNum());
        String encryptedAccessToken = aesEncryptor.encrypt(token.accessToken());
        String encryptedRefreshToken = aesEncryptor.encrypt(token.refreshToken());
        Instant tokenExpiresAt = Instant.now().plusSeconds(token.expiresIn());

        BankAccount bankAccount = bankAccountRepository.findByCompanyId(companyId).orElse(null);
        if (bankAccount == null) {
            bankAccount = BankAccount.register(
                companyId, account.bankName(), account.accountNumMasked(),
                encryptedFintechUseNum, encryptedAccessToken, encryptedRefreshToken, tokenExpiresAt
            );
        } else {
            bankAccount.reconnect(
                account.bankName(), account.accountNumMasked(),
                encryptedFintechUseNum, encryptedAccessToken, encryptedRefreshToken, tokenExpiresAt
            );
        }

        // saveAndFlush — 신규 등록 시 @CreationTimestamp(createdAt)는 flush 시점에 채워진다.
        return BankAccountDto.Response.connected(bankAccountRepository.saveAndFlush(bankAccount));
    }

    @Transactional(readOnly = true)
    public BankAccountDto.Response getStatus(EmployeePrincipal principal) {
        requireAdmin(principal);
        UUID companyId = UUID.fromString(principal.companyId());
        return bankAccountRepository.findByCompanyId(companyId)
            .map(BankAccountDto.Response::connected)
            .orElseGet(BankAccountDto.Response::notConnected);
    }

    private void requireAdmin(EmployeePrincipal principal) {
        if (!principal.isAdmin()) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "이 작업을 수행할 권한이 없습니다");
        }
    }
}
