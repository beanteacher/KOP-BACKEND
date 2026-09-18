package com.kop.finance.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * 05-database-schema.md finance.bank_accounts — 오픈뱅킹 연동 계좌, 회사당 1개(company_id UNIQUE).
 * fintechUseNum/accessToken/refreshToken은 평문이 아니라 AesEncryptor로 암호화한 암호문을
 * 저장한다(09-security.md 예외 조항, auth-service Company.bankAccountNumber와 같은 패턴) —
 * 암호화/복호화는 이 엔티티가 아니라 Service 계층에서 한다.
 */
@Entity
@Table(name = "bank_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BankAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "company_id", nullable = false, unique = true)
    private UUID companyId;

    @Column(name = "bank_name", length = 50)
    private String bankName;

    @Column(name = "account_number_masked", length = 50)
    private String accountNumberMasked;

    @Column(name = "fintech_use_num", nullable = false, length = 500)
    private String fintechUseNum;

    @Column(name = "access_token", nullable = false, length = 1000)
    private String accessToken;

    @Column(name = "refresh_token", nullable = false, length = 1000)
    private String refreshToken;

    @Column(name = "token_expires_at", nullable = false)
    private Instant tokenExpiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private BankAccount(
        UUID companyId, String bankName, String accountNumberMasked,
        String fintechUseNum, String accessToken, String refreshToken, Instant tokenExpiresAt
    ) {
        this.companyId = companyId;
        this.bankName = bankName;
        this.accountNumberMasked = accountNumberMasked;
        this.fintechUseNum = fintechUseNum;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenExpiresAt = tokenExpiresAt;
    }

    /** 계좌 등록(암호화된 값을 인자로 받는다 — 평문은 이 엔티티에 들어오지 않는다). */
    public static BankAccount register(
        UUID companyId, String bankName, String accountNumberMasked,
        String encryptedFintechUseNum, String encryptedAccessToken, String encryptedRefreshToken, Instant tokenExpiresAt
    ) {
        return new BankAccount(companyId, bankName, accountNumberMasked, encryptedFintechUseNum, encryptedAccessToken, encryptedRefreshToken, tokenExpiresAt);
    }

    /** 액세스 토큰 갱신(refresh) 결과 반영. */
    public void updateTokens(String encryptedAccessToken, String encryptedRefreshToken, Instant tokenExpiresAt) {
        this.accessToken = encryptedAccessToken;
        this.refreshToken = encryptedRefreshToken;
        this.tokenExpiresAt = tokenExpiresAt;
    }
}
