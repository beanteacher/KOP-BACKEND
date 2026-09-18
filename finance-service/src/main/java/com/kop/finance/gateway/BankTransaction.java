package com.kop.finance.gateway;

import java.time.LocalDateTime;

/**
 * 오픈뱅킹 거래내역조회 API(GET /v2.0/account/transaction_list/fin_num) 응답 res_list[] 한 건을
 * 표현하는 포트 레벨 값 객체 — JPA 엔티티가 아니다. 03-feature-spec.md 입금-영수증 매칭(금액
 * 완전일치 + printedContent가 vendorName을 포함)에 쓰인다.
 *
 * 주의: 이 API는 거래 단위 고유번호(transactionId)를 주지 않는다 — 응답에 있는 bank_tran_id는
 * "우리가 이 조회 요청 하나에 부여한 번호"일 뿐 개별 거래 식별자가 아니다(공식 API 명세서
 * §2.3.2 확인). 입금자명도 별도 필드가 없다 — printedContent(통장인자내용, print_content)가
 * 실무상 입금자명이 찍히는 자리라 매칭에 이 필드를 쓴다.
 */
public record BankTransaction(
    LocalDateTime transactionDateTime,
    TransactionType type,
    Long amount,
    String printedContent,
    Long balanceAfter
) {
    public enum TransactionType {
        DEPOSIT,
        WITHDRAWAL,
        OTHER
    }
}
