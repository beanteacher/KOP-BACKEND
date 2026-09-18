package com.kop.finance.gateway;

import java.time.Instant;

/**
 * 오픈뱅킹 거래내역조회 API 응답 한 건을 표현하는 포트 레벨 값 객체 — JPA 엔티티가 아니다.
 * 03-feature-spec.md 입금-영수증 매칭(금액 완전일치 + counterpartyName이 vendorName을 포함)에
 * 쓰인다. transactionId는 은행/오픈뱅킹이 부여하는 거래 고유번호 — 중복 매칭 방지(멱등성)에 쓴다.
 */
public record BankTransaction(
    String transactionId,
    Instant transactionDateTime,
    TransactionType type,
    Long amount,
    String counterpartyName,
    Long balanceAfter
) {
    public enum TransactionType {
        DEPOSIT,
        WITHDRAWAL
    }
}
