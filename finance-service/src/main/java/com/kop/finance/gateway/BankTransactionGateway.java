package com.kop.finance.gateway;

import com.kop.finance.domain.BankAccount;

import java.time.LocalDate;
import java.util.List;

/**
 * 오픈뱅킹 거래내역조회 API에 대한 포트(인터페이스) — 발행 방식을 ExcelIssuanceGateway/
 * HometaxApiIssuanceGateway처럼 구현체로 분리하는 것과 같은 패턴(03-feature-spec.md).
 * 현재 활성 구현체는 {@link MockBankTransactionGateway} 하나뿐이다 — 사용자가 오픈뱅킹
 * 개발자센터 이용기관 등록(심사)을 마치기 전까지는 실제 API를 호출하는 구현체를 붙이지 않는다.
 * 실제 HTTP 클라이언트 구현체가 생기면 프로필/설정값으로 활성 빈을 전환한다.
 */
public interface BankTransactionGateway {

    List<BankTransaction> fetchTransactions(BankAccount account, LocalDate from, LocalDate to);
}
