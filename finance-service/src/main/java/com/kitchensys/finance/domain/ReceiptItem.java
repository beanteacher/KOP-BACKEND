package com.kitchensys.finance.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 05-database-schema.md finance.receipt_items — tax_invoice_items와 같은 패턴. Receipt와
 * 같은 스키마(finance) 안이라 company_id/created_by와 달리 진짜 FK 관계로 맺는다.
 */
@Entity
@Table(name = "receipt_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReceiptItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_id", nullable = false)
    private Receipt receipt;

    @Column(nullable = false, length = 100)
    private String name;

    /** 견적・납품서 인쇄 뷰의 규격 컬럼 — 선택 입력이라 값이 없는 기존 품목은 null. */
    @Column(length = 100)
    private String spec;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false)
    private Long unitPrice;

    /** quantity × unitPrice — 클라이언트가 보낸 값을 신뢰하지 않고 항상 서버가 재계산한다. */
    @Column(nullable = false)
    private Long amount;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder;

    private ReceiptItem(Receipt receipt, String name, String spec, Integer quantity, Long unitPrice, short sortOrder) {
        this.receipt = receipt;
        this.name = name;
        this.spec = spec;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.amount = unitPrice * quantity;
        this.sortOrder = sortOrder;
    }

    static ReceiptItem of(Receipt receipt, String name, String spec, Integer quantity, Long unitPrice, int sortOrder) {
        return new ReceiptItem(receipt, name, spec, quantity, unitPrice, (short) sortOrder);
    }
}
