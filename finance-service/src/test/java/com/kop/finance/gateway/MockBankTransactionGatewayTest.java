package com.kop.finance.gateway;

import com.kop.finance.domain.BankAccount;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MockBankTransactionGatewayTest {

    MockBankTransactionGateway gateway = new MockBankTransactionGateway();

    @Test
    void 항상_빈_목록을_반환한다() {
        BankAccount account = BankAccount.register(
            UUID.randomUUID(), "국민은행", "110-***-1234", "enc-fintech", "enc-access", "enc-refresh", Instant.now()
        );
        ReflectionTestUtils.setField(account, "id", UUID.randomUUID());

        var transactions = gateway.fetchTransactions(account, LocalDate.now().minusDays(7), LocalDate.now());

        assertThat(transactions).isEmpty();
    }
}
