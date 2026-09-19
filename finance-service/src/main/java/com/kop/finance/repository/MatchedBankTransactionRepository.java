package com.kop.finance.repository;

import com.kop.finance.domain.MatchedBankTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.UUID;

public interface MatchedBankTransactionRepository extends JpaRepository<MatchedBankTransaction, UUID> {

    boolean existsByCompanyIdAndTransactionDateTimeAndAmountAndPrintedContent(
        UUID companyId, LocalDateTime transactionDateTime, Long amount, String printedContent
    );
}
