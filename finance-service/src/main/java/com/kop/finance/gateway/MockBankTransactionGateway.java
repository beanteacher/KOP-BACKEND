package com.kop.finance.gateway;

import com.kop.finance.domain.BankAccount;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * {@link BankTransactionGateway} 기본(유일) 구현체 — 항상 빈 목록을 반환한다. 실제 은행 데이터를
 * 흉내 낸 가짜 거래를 만들지 않는다: 입금-매칭 로직을 만들 때는 이 클래스가 아니라 Mockito로
 * {@link BankTransactionGateway}를 직접 스텁해서 테스트한다 — 여기서 그럴듯한 샘플 데이터를
 * 반환하면 "매칭이 동작하는 것처럼 보이지만 실제로는 아무 은행 데이터도 없는" 착시가 생긴다.
 */
@Component
public class MockBankTransactionGateway implements BankTransactionGateway {

    @Override
    public List<BankTransaction> fetchTransactions(BankAccount account, LocalDate from, LocalDate to) {
        return List.of();
    }
}
