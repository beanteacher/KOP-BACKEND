package com.kitchensys.finance.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 05-database-schema.md finance.receipts. company_id/created_by/client_id는 다른 서비스
 * 소유 테이블(또는 아직 없는 테이블)을 가리키는 UUID일 뿐 FK로 걸지 않는다 — MSA 스키마 간
 * 직접 참조 금지(04-system-architecture.md).
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

    @Column(nullable = false)
    private Long amount;

    @Column(name = "vendor_name", nullable = false, length = 100)
    private String vendorName;

    @Column(name = "client_id")
    private UUID clientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReceiptCategory category;

    @Column(length = 200)
    private String memo;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private Receipt(
        UUID companyId, UUID createdBy, LocalDate receiptDate, Long amount,
        String vendorName, UUID clientId, ReceiptCategory category, String memo
    ) {
        this.companyId = companyId;
        this.createdBy = createdBy;
        this.receiptDate = receiptDate;
        this.amount = amount;
        this.vendorName = vendorName;
        this.clientId = clientId;
        this.category = category;
        this.memo = memo;
    }

    /** A-1 영수증 등록. */
    public static Receipt register(
        UUID companyId, UUID createdBy, LocalDate receiptDate, Long amount,
        String vendorName, UUID clientId, ReceiptCategory category, String memo
    ) {
        return new Receipt(companyId, createdBy, receiptDate, amount, vendorName, clientId, category, memo);
    }

    /** A-3 영수증 수정 — 필드 전체 교체. */
    public void update(LocalDate receiptDate, Long amount, String vendorName, UUID clientId, ReceiptCategory category, String memo) {
        this.receiptDate = receiptDate;
        this.amount = amount;
        this.vendorName = vendorName;
        this.clientId = clientId;
        this.category = category;
        this.memo = memo;
    }

    /** A-3 소프트 삭제. */
    public void softDelete() {
        this.deletedAt = Instant.now();
    }

    public boolean isOwnedBy(UUID employeeId) {
        return createdBy.equals(employeeId);
    }

    /** A-3 "직원은 등록 후 24시간 이내만" — 등록 시각(createdAt) 기준. */
    public boolean isWithinStaffEditWindow(Instant now) {
        return Duration.between(createdAt, now).compareTo(STAFF_EDIT_WINDOW) <= 0;
    }
}
