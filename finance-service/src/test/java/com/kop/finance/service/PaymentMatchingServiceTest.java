package com.kop.finance.service;

import com.kop.finance.domain.BankAccount;
import com.kop.finance.domain.MatchedBankTransaction;
import com.kop.finance.domain.PaymentStatus;
import com.kop.finance.domain.Receipt;
import com.kop.finance.gateway.BankTransaction;
import com.kop.finance.gateway.BankTransactionGateway;
import com.kop.finance.repository.BankAccountRepository;
import com.kop.finance.repository.MatchedBankTransactionRepository;
import com.kop.finance.repository.ReceiptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentMatchingServiceTest {

    @Mock BankAccountRepository bankAccountRepository;
    @Mock ReceiptRepository receiptRepository;
    @Mock MatchedBankTransactionRepository matchedBankTransactionRepository;
    @Mock BankTransactionGateway bankTransactionGateway;
    @Mock TaxInvoiceAutoIssuanceService taxInvoiceAutoIssuanceService;

    PaymentMatchingService service;

    UUID companyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PaymentMatchingService(
            bankAccountRepository, receiptRepository, matchedBankTransactionRepository, bankTransactionGateway, taxInvoiceAutoIssuanceService
        );
    }

    private BankAccount bankAccountOf() {
        return BankAccount.register(companyId, "국민은행", "123-**-****", "enc-fintech", "enc-access", "enc-refresh", Instant.now().plusSeconds(3600));
    }

    private Receipt pendingReceiptOf(String vendorName, long amount) {
        Receipt receipt = Receipt.register(companyId, UUID.randomUUID(), LocalDate.now(), vendorName, null, null);
        receipt.replaceItems(List.of(new Receipt.ItemInput("품목", null, 1, amount)));
        ReflectionTestUtils.setField(receipt, "id", UUID.randomUUID());
        return receipt;
    }

    private BankTransaction depositOf(long amount, String printedContent) {
        return new BankTransaction(LocalDateTime.now(), BankTransaction.TransactionType.DEPOSIT, amount, printedContent, 1_000_000L);
    }

    private void stubLedgerSaveAndFlush() {
        when(matchedBankTransactionRepository.saveAndFlush(any())).thenAnswer(inv -> {
            MatchedBankTransaction entry = inv.getArgument(0);
            ReflectionTestUtils.setField(entry, "id", UUID.randomUUID());
            return entry;
        });
    }

    @Test
    void 연결된_계좌가_없으면_0건_0건을_반환한다() {
        when(bankAccountRepository.findByCompanyId(companyId)).thenReturn(Optional.empty());

        PaymentMatchingService.MatchResult result = service.matchForCompany(companyId);

        assertThat(result.pendingCount()).isEqualTo(0);
        assertThat(result.matchedCount()).isEqualTo(0);
        verifyNoInteractions(bankTransactionGateway);
    }

    @Test
    void PENDING_영수증이_없으면_거래내역을_조회하지_않는다() {
        when(bankAccountRepository.findByCompanyId(companyId)).thenReturn(Optional.of(bankAccountOf()));
        when(receiptRepository.findByCompanyIdAndPaymentStatusAndDeletedAtIsNullOrderByReceiptDateAsc(companyId, PaymentStatus.PENDING))
            .thenReturn(List.of());

        PaymentMatchingService.MatchResult result = service.matchForCompany(companyId);

        assertThat(result.pendingCount()).isEqualTo(0);
        verifyNoInteractions(bankTransactionGateway);
    }

    @Test
    void 금액과_거래처명이_유일하게_일치하면_입금확인과_자동발행을_수행한다() {
        Receipt receipt = pendingReceiptOf("한성식자재", 10_000L);
        when(bankAccountRepository.findByCompanyId(companyId)).thenReturn(Optional.of(bankAccountOf()));
        when(receiptRepository.findByCompanyIdAndPaymentStatusAndDeletedAtIsNullOrderByReceiptDateAsc(companyId, PaymentStatus.PENDING))
            .thenReturn(List.of(receipt));
        when(bankTransactionGateway.fetchTransactions(any(), any(), any()))
            .thenReturn(List.of(depositOf(10_000L, "한성식자재 입금")));
        stubLedgerSaveAndFlush();

        PaymentMatchingService.MatchResult result = service.matchForCompany(companyId);

        assertThat(result.pendingCount()).isEqualTo(1);
        assertThat(result.matchedCount()).isEqualTo(1);
        assertThat(receipt.isPending()).isFalse();
        assertThat(receipt.getMatchedTransactionId()).isNotNull();
        verify(taxInvoiceAutoIssuanceService).issueIfEligible(receipt);
    }

    @Test
    void 금액이_같은_후보가_2건이면_모호해서_건너뛴다() {
        Receipt receipt = pendingReceiptOf("한성식자재", 10_000L);
        when(bankAccountRepository.findByCompanyId(companyId)).thenReturn(Optional.of(bankAccountOf()));
        when(receiptRepository.findByCompanyIdAndPaymentStatusAndDeletedAtIsNullOrderByReceiptDateAsc(companyId, PaymentStatus.PENDING))
            .thenReturn(List.of(receipt));
        when(bankTransactionGateway.fetchTransactions(any(), any(), any()))
            .thenReturn(List.of(depositOf(10_000L, "한성식자재 입금1"), depositOf(10_000L, "한성식자재 입금2")));

        PaymentMatchingService.MatchResult result = service.matchForCompany(companyId);

        assertThat(result.matchedCount()).isEqualTo(0);
        assertThat(receipt.isPending()).isTrue();
        verify(matchedBankTransactionRepository, never()).saveAndFlush(any());
        verifyNoInteractions(taxInvoiceAutoIssuanceService);
    }

    @Test
    void 거래처명이_통장인자내용에_없으면_건너뛴다() {
        Receipt receipt = pendingReceiptOf("한성식자재", 10_000L);
        when(bankAccountRepository.findByCompanyId(companyId)).thenReturn(Optional.of(bankAccountOf()));
        when(receiptRepository.findByCompanyIdAndPaymentStatusAndDeletedAtIsNullOrderByReceiptDateAsc(companyId, PaymentStatus.PENDING))
            .thenReturn(List.of(receipt));
        when(bankTransactionGateway.fetchTransactions(any(), any(), any()))
            .thenReturn(List.of(depositOf(10_000L, "다른상호 입금")));

        PaymentMatchingService.MatchResult result = service.matchForCompany(companyId);

        assertThat(result.matchedCount()).isEqualTo(0);
        assertThat(receipt.isPending()).isTrue();
    }

    @Test
    void 이미_다른_영수증에_매칭된_거래는_후보에서_제외한다() {
        Receipt receipt = pendingReceiptOf("한성식자재", 10_000L);
        BankTransaction deposit = depositOf(10_000L, "한성식자재 입금");
        when(bankAccountRepository.findByCompanyId(companyId)).thenReturn(Optional.of(bankAccountOf()));
        when(receiptRepository.findByCompanyIdAndPaymentStatusAndDeletedAtIsNullOrderByReceiptDateAsc(companyId, PaymentStatus.PENDING))
            .thenReturn(List.of(receipt));
        when(bankTransactionGateway.fetchTransactions(any(), any(), any())).thenReturn(List.of(deposit));
        when(matchedBankTransactionRepository.existsByCompanyIdAndTransactionDateTimeAndAmountAndPrintedContent(
            companyId, deposit.transactionDateTime(), deposit.amount(), deposit.printedContent()
        )).thenReturn(true);

        PaymentMatchingService.MatchResult result = service.matchForCompany(companyId);

        assertThat(result.matchedCount()).isEqualTo(0);
        assertThat(receipt.isPending()).isTrue();
        verify(matchedBankTransactionRepository, never()).saveAndFlush(any());
    }
}
