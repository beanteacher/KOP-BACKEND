package com.kop.finance.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 05-database-schema.md 확장(V8) — 오픈뱅킹 입금 건과 영수증을 매칭한 기록(멱등성 원장).
 * 실거래 API가 거래 고유 ID를 주지 않아서(BankTransaction 참고) (company_id, 일시, 금액,
 * 통장인자내용) 조합을 자연키로 삼는다 — 같은 조회를 여러 번 돌려도 같은 입금을 두 번
 * 매칭하지 않는다.
 */
@Entity
@Table(name = "matched_bank_transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchedBankTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "transaction_datetime", nullable = false)
    private LocalDateTime transactionDateTime;

    @Column(nullable = false)
    private Long amount;

    @Column(name = "printed_content", nullable = false, length = 200)
    private String printedContent;

    @Column(name = "matched_receipt_id", nullable = false)
    private UUID matchedReceiptId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private MatchedBankTransaction(
        UUID companyId, LocalDateTime transactionDateTime, Long amount, String printedContent, UUID matchedReceiptId
    ) {
        this.companyId = companyId;
        this.transactionDateTime = transactionDateTime;
        this.amount = amount;
        this.printedContent = printedContent;
        this.matchedReceiptId = matchedReceiptId;
    }

    public static MatchedBankTransaction record(
        UUID companyId, LocalDateTime transactionDateTime, Long amount, String printedContent, UUID matchedReceiptId
    ) {
        return new MatchedBankTransaction(companyId, transactionDateTime, amount, printedContent, matchedReceiptId);
    }
}
