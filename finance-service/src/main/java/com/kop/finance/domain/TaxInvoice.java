package com.kop.finance.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 03-feature-spec.md 모듈 B(세금계산서) B-2~B-4, 05-database-schema.md finance.tax_invoices.
 * 공급자(자사) 정보는 여기 저장하지 않는다 — auth-service Company를 그대로 쓰고(MSA 스키마 격리,
 * FK 없음), 화면 조합은 프론트가 이미 갖고 있는 /api/auth/me·company 응답과 합쳐서 한다
 * (ReceiptSlip이 회사 정보를 조합하는 것과 같은 패턴).
 */
@Entity
@Table(name = "tax_invoices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaxInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaxInvoiceStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "issue_method", nullable = false, length = 20)
    private TaxInvoiceIssueMethod issueMethod;

    /** 품목 합계 — 애플리케이션이 계산해 저장한다. {@link #replaceItems} 참고. */
    @Column(name = "supply_amount", nullable = false)
    private Long supplyAmount = 0L;

    @Column(name = "tax_amount", nullable = false)
    private Long taxAmount = 0L;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount = 0L;

    @Column(length = 200)
    private String note;

    /** 수정 발행 시 원본 참조(B-4) — 같은 테이블 자기참조라 연관관계 대신 단순 UUID로 둔다. */
    @Column(name = "revised_from_id")
    private UUID revisedFromId;

    /** 입금 매칭 자동발행(PaymentMatchingService)의 출처 영수증 — 수동 작성 건은 null. */
    @Column(name = "source_receipt_id")
    private UUID sourceReceiptId;

    @Column(name = "cancel_reason", length = 100)
    private String cancelReason;

    @Column(name = "canceled_at")
    private Instant canceledAt;

    @Column(name = "excel_downloaded_at")
    private Instant excelDownloadedAt;

    @OneToMany(mappedBy = "taxInvoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder asc")
    private List<TaxInvoiceItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private TaxInvoice(
        UUID companyId, UUID createdBy, Client client, LocalDate issueDate, TaxInvoiceStatus status, String note,
        UUID revisedFromId, UUID sourceReceiptId
    ) {
        this.companyId = companyId;
        this.createdBy = createdBy;
        this.client = client;
        this.issueDate = issueDate;
        this.status = status;
        this.issueMethod = TaxInvoiceIssueMethod.EXCEL; // 지금은 EXCEL만 실제 동작(HOMETAX_API는 유료 플랜 스텁)
        this.note = note;
        this.revisedFromId = revisedFromId;
        this.sourceReceiptId = sourceReceiptId;
    }

    /** B-2 세금계산서 작성 — status는 DRAFT 또는 COMPLETED 중 요청 시점에 결정(Service에서 검증). */
    public static TaxInvoice create(UUID companyId, UUID createdBy, Client client, LocalDate issueDate, TaxInvoiceStatus status, String note) {
        return new TaxInvoice(companyId, createdBy, client, issueDate, status, note, null, null);
    }

    /**
     * 입금 매칭 자동발행(PaymentMatchingService/TaxInvoiceAutoIssuanceService) — 영수증 입금이
     * 확인되면 관리자 개입 없이 바로 COMPLETED 상태로 만든다. createdBy는 매칭을 수행한 시스템
     * 주체가 아니라 원 영수증 작성자를 그대로 쓴다(감사 추적상 "누가 이 매출을 등록했는가"가
     * "누가 입금 확인 버튼을 눌렀는가"보다 의미 있는 값이라 판단).
     */
    public static TaxInvoice createFromReceipt(UUID companyId, UUID createdBy, Client client, LocalDate issueDate, String note, UUID sourceReceiptId) {
        return new TaxInvoice(companyId, createdBy, client, issueDate, TaxInvoiceStatus.COMPLETED, note, null, sourceReceiptId);
    }

    /**
     * B-4 수정 발행 — 새 값으로 새 건을 만들되 원본을 revisedFromId로 참조한다(품목은 이후
     * replaceItems로 채운다). 필드는 원본을 그대로 베끼지 않는다 — "수정"이 요청의 목적이라
     * Service가 새 요청 값(클라이언트·작성일자·상태·비고)을 그대로 넘긴다.
     */
    public static TaxInvoice createRevision(
        UUID companyId, UUID createdBy, Client client, LocalDate issueDate, TaxInvoiceStatus status, String note, UUID revisedFromId
    ) {
        return new TaxInvoice(companyId, createdBy, client, issueDate, status, note, revisedFromId, null);
    }

    /** 등록·수정 양쪽에서 품목 전체를 교체하고 공급가액·세액·합계를 다시 계산한다. */
    public void replaceItems(List<ItemInput> inputs) {
        items.clear();
        int order = 0;
        long supplySum = 0;
        long taxSum = 0;
        for (ItemInput input : inputs) {
            TaxInvoiceItem item = TaxInvoiceItem.of(this, input.name(), input.spec(), input.quantity(), input.unitPrice(), order++);
            items.add(item);
            supplySum += item.getSupplyAmount();
            taxSum += item.getTaxAmount();
        }
        this.supplyAmount = supplySum;
        this.taxAmount = taxSum;
        this.totalAmount = supplySum + taxSum;
    }

    public boolean isDraft() {
        return status == TaxInvoiceStatus.DRAFT;
    }

    public boolean isCanceled() {
        return status == TaxInvoiceStatus.CANCELED;
    }

    /** B-2 "엑셀 다운로드 시 상태: 엑셀다운로드완료로 변경". */
    public void markExcelDownloaded() {
        this.status = TaxInvoiceStatus.EXCEL_DOWNLOADED;
        this.excelDownloadedAt = Instant.now();
    }

    /** B-4 "원본 건은 수정발행됨 상태로 변경(삭제 아님)". */
    public void markRevised() {
        this.status = TaxInvoiceStatus.REVISED;
    }

    /** B-4 취소 — 사유 필수. */
    public void cancel(String reason) {
        this.status = TaxInvoiceStatus.CANCELED;
        this.cancelReason = reason;
        this.canceledAt = Instant.now();
    }

    public record ItemInput(String name, String spec, Integer quantity, Long unitPrice) {}
}
