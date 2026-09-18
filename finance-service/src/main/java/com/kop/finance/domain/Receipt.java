package com.kop.finance.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 05-database-schema.md finance.receipts. company_id/created_by/client_id는 다른 서비스
 * 소유 테이블(또는 아직 없는 테이블)을 가리키는 UUID일 뿐 FK로 걸지 않는다 — MSA 스키마 간
 * 직접 참조 금지(04-system-architecture.md). items는 같은 스키마 안이라 진짜 연관관계다.
 */
@Entity
@Table(name = "receipts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Receipt {

    private static final Duration STAFF_EDIT_WINDOW = Duration.ofHours(24);

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "receipt_date", nullable = false)
    private LocalDate receiptDate;

    /** 품목(items) 합계 — 애플리케이션이 계산해 저장한다. {@link #replaceItems} 참고. */
    @Column(nullable = false)
    private Long amount;

    @Column(name = "vendor_name", nullable = false, length = 100)
    private String vendorName;

    @Column(name = "client_id")
    private UUID clientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus paymentStatus;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "matched_transaction_id")
    private UUID matchedTransactionId;

    @Column(length = 200)
    private String memo;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder asc")
    private List<ReceiptItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private Receipt(UUID companyId, UUID createdBy, LocalDate receiptDate, String vendorName, UUID clientId, String memo) {
        this.companyId = companyId;
        this.createdBy = createdBy;
        this.receiptDate = receiptDate;
        this.amount = 0L;
        this.vendorName = vendorName;
        this.clientId = clientId;
        this.paymentStatus = PaymentStatus.PENDING;
        this.memo = memo;
    }

    /** A-1 영수증 등록 — 품목은 register() 직후 {@link #replaceItems}로 채운다. */
    public static Receipt register(UUID companyId, UUID createdBy, LocalDate receiptDate, String vendorName, UUID clientId, String memo) {
        return new Receipt(companyId, createdBy, receiptDate, vendorName, clientId, memo);
    }

    /** A-3 영수증 수정 — 품목을 제외한 필드 전체 교체. */
    public void update(LocalDate receiptDate, String vendorName, UUID clientId, String memo) {
        this.receiptDate = receiptDate;
        this.vendorName = vendorName;
        this.clientId = clientId;
        this.memo = memo;
    }

    /**
     * 등록·수정 양쪽에서 품목 전체를 교체하고 합계를 다시 계산한다. 품목별 금액(quantity ×
     * unitPrice)은 신뢰할 수 없는 클라이언트 입력이 아니라 여기서 서버가 다시 계산한다.
     */
    public void replaceItems(List<ItemInput> inputs) {
        items.clear();
        int order = 0;
        for (ItemInput input : inputs) {
            items.add(ReceiptItem.of(this, input.name(), input.spec(), input.quantity(), input.unitPrice(), order++));
        }
        this.amount = items.stream().mapToLong(ReceiptItem::getAmount).sum();
    }

    /** A-3 소프트 삭제. */
    public void softDelete() {
        this.deletedAt = Instant.now();
    }

    /** 입금 확인 — 상태 전이 가능 여부는 호출 측(Service)이 미리 검사한다. */
    public void markPaid(UUID matchedTransactionId) {
        this.paymentStatus = PaymentStatus.PAID;
        this.paidAt = Instant.now();
        this.matchedTransactionId = matchedTransactionId;
    }

    /** 영수증 취소 — 상태 전이 가능 여부는 호출 측(Service)이 미리 검사한다. */
    public void cancel() {
        this.paymentStatus = PaymentStatus.CANCELED;
    }

    public boolean isPending() {
        return paymentStatus == PaymentStatus.PENDING;
    }

    public boolean isOwnedBy(UUID employeeId) {
        return createdBy.equals(employeeId);
    }

    /** A-3 "직원은 등록 후 24시간 이내만" — 등록 시각(createdAt) 기준. */
    public boolean isWithinStaffEditWindow(Instant now) {
        return Duration.between(createdAt, now).compareTo(STAFF_EDIT_WINDOW) <= 0;
    }

    public record ItemInput(String name, String spec, Integer quantity, Long unitPrice) {}
}
