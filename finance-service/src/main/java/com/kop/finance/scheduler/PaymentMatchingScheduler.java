package com.kop.finance.scheduler;

import com.kop.finance.repository.BankAccountRepository;
import com.kop.finance.service.PaymentMatchingService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 로드맵 마지막 조각의 "자동" 부분 — 관리자가 매번 수동으로 /api/receipts/match-payments를
 * 호출하지 않아도, 계좌가 연결된 회사마다 주기적으로 입금 내역을 조회해 PENDING 영수증과
 * 매칭한다. 한 회사 매칭 실패가 다른 회사 매칭을 막지 않도록 회사별로 독립 처리한다.
 */
@Component
@RequiredArgsConstructor
public class PaymentMatchingScheduler {

    private final BankAccountRepository bankAccountRepository;
    private final PaymentMatchingService paymentMatchingService;

    @Scheduled(fixedDelayString = "${payment-matching.fixed-delay-ms}")
    public void matchAllCompanies() {
        bankAccountRepository.findAll().forEach(account -> paymentMatchingService.matchForCompany(account.getCompanyId()));
    }
}
