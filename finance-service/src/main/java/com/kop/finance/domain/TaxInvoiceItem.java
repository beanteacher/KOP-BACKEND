package com.kop.finance.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 05-database-schema.md finance.tax_invoice_items — receipt_items와 같은 패턴, 같은 스키마 안이라
 * 진짜 FK(연관관계)로 건다. supply_amount/tax_amount는 애플리케이션이 계산해 저장한다(감사 추적).
 */
@Entity
@Table(name = "tax_invoice_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaxInvoiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tax_invoice_id", nullable = false)
    private TaxInvoice taxInvoice;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 100)
    private String spec;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false)
    private Long unitPrice;

    /** quantity × unitPrice — 클라이언트가 보낸 값을 신뢰하지 않고 항상 서버가 재계산한다. */
    @Column(name = "supply_amount", nullable = false)
    private Long supplyAmount;

    /** supplyAmount × 10%(부가세율 고정), 원 단위 반올림. */
    @Column(name = "tax_amount", nullable = false)
    private Long taxAmount;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder;

    private TaxInvoiceItem(TaxInvoice taxInvoice, String name, String spec, Integer quantity, Long unitPrice, short sortOrder) {
        this.taxInvoice = taxInvoice;
        this.name = name;
        this.spec = spec;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.supplyAmount = unitPrice * quantity;
        this.taxAmount = Math.round(this.supplyAmount / 10.0);
        this.sortOrder = sortOrder;
    }

    static TaxInvoiceItem of(TaxInvoice taxInvoice, String name, String spec, Integer quantity, Long unitPrice, int sortOrder) {
        return new TaxInvoiceItem(taxInvoice, name, spec, quantity, unitPrice, (short) sortOrder);
    }
}
