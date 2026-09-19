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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 로드맵 마지막 조각 — 오픈뱅킹 입금 내역을 미확인(PENDING) 영수증과 매칭해 자동으로 입금
 * 확인 처리한다. 거래내역 API가 거래 고유 ID를 주지 않아서(BankTransaction 참고) "금액 정확히
 * 일치 + 통장인자내용에 거래처명 포함" 두 조건 모두를 만족하는 입금이 정확히 1건일 때만
 * 매칭한다 — 후보가 0건이거나 2건 이상이면 오매칭 위험이 더 크므로 건너뛰고 관리자의 수동
 * 확인(ReceiptService.markPaid)에 맡긴다.
 */
@Service
@RequiredArgsConstructor
public class PaymentMatchingService {

    private static final int LOOKBACK_DAYS = 90;

    private final BankAccountRepository bankAccountRepository;
    private final ReceiptRepository receiptRepository;
    private final MatchedBankTransactionRepository matchedBankTransactionRepository;
    private final BankTransactionGateway bankTransactionGateway;
    private final TaxInvoiceAutoIssuanceService taxInvoiceAutoIssuanceService;

    @Transactional
    public MatchResult matchForCompany(UUID companyId) {
        BankAccount account = bankAccountRepository.findByCompanyId(companyId).orElse(null);
        if (account == null) {
            return new MatchResult(0, 0);
        }

        List<Receipt> pending = receiptRepository.findByCompanyIdAndPaymentStatusAndDeletedAtIsNullOrderByReceiptDateAsc(companyId, PaymentStatus.PENDING);
        if (pending.isEmpty()) {
            return new MatchResult(0, 0);
        }

        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(LOOKBACK_DAYS);
        List<BankTransaction> deposits = bankTransactionGateway.fetchTransactions(account, from, to).stream()
            .filter(t -> t.type() == BankTransaction.TransactionType.DEPOSIT)
            .toList();

        int matchedCount = 0;
        for (Receipt receipt : pending) {
            BankTransaction match = findUnambiguousMatch(companyId, receipt, deposits);
            if (match == null) {
                continue;
            }

            MatchedBankTransaction ledgerEntry = MatchedBankTransaction.record(
                companyId, match.transactionDateTime(), match.amount(), match.printedContent(), receipt.getId()
            );
            matchedBankTransactionRepository.saveAndFlush(ledgerEntry);

            receipt.markPaid(ledgerEntry.getId());
            taxInvoiceAutoIssuanceService.issueIfEligible(receipt);
            matchedCount++;
        }

        return new MatchResult(pending.size(), matchedCount);
    }

    private BankTransaction findUnambiguousMatch(UUID companyId, Receipt receipt, List<BankTransaction> deposits) {
        List<BankTransaction> candidates = new ArrayList<>();
        for (BankTransaction transaction : deposits) {
            if (!receipt.getAmount().equals(transaction.amount())) {
                continue;
            }
            if (!printedContentMentionsVendor(transaction.printedContent(), receipt.getVendorName())) {
                continue;
            }
            if (matchedBankTransactionRepository.existsByCompanyIdAndTransactionDateTimeAndAmountAndPrintedContent(
                companyId, transaction.transactionDateTime(), transaction.amount(), transaction.printedContent()
            )) {
                continue; // 이미 다른 영수증에 매칭된(또는 직전 루프에서 소비된) 입금 — 재사용 금지
            }
            candidates.add(transaction);
        }
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    /** 통장인자내용(print_content)은 은행마다 형식이 제각각이라 공백 무시 + 대소문자 무시 부분일치로 비교한다. */
    private boolean printedContentMentionsVendor(String printedContent, String vendorName) {
        if (printedContent == null || vendorName == null || vendorName.isBlank()) {
            return false;
        }
        String normalizedContent = printedContent.replace(" ", "").toLowerCase(Locale.KOREA);
        String normalizedVendor = vendorName.replace(" ", "").toLowerCase(Locale.KOREA);
        return normalizedVendor.isEmpty() || normalizedContent.contains(normalizedVendor);
    }

    public record MatchResult(int pendingCount, int matchedCount) {}
}
