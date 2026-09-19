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

    private TaxInvoiceItem(
        TaxInvoice taxInvoice, String name, String spec, Integer quantity, Long unitPrice, short sortOrder,
        Long supplyAmount, Long taxAmount
    ) {
        this.taxInvoice = taxInvoice;
        this.name = name;
        this.spec = spec;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.supplyAmount = supplyAmount;
        this.taxAmount = taxAmount;
        this.sortOrder = sortOrder;
    }

    /** 수동 작성(TaxInvoiceService) — unitPrice는 부가세 별도(세전) 단가, 세액은 그 위에 10%를 더한다. */
    static TaxInvoiceItem of(TaxInvoice taxInvoice, String name, String spec, Integer quantity, Long unitPrice, int sortOrder) {
        long supplyAmount = unitPrice * quantity;
        long taxAmount = Math.round(supplyAmount / 10.0);
        return new TaxInvoiceItem(taxInvoice, name, spec, quantity, unitPrice, (short) sortOrder, supplyAmount, taxAmount);
    }

    /**
     * 영수증 자동발행 중 부가세를 별도로 못 받은 거래처(TaxInvoiceAutoIssuanceService) — unitPrice ×
     * quantity 자체가 이미 실제로 받은 최종 금액이라고 보고 거꾸로 공급가액·세액을 나눈다
     * (공급가액 = 총액 ÷ 1.1, 세액 = 총액 − 공급가액). 우리가 부가세를 대신 떠안는 만큼 공급가액이
     * 줄어드는 걸 그대로 반영한다.
     */
    static TaxInvoiceItem ofInclusive(TaxInvoice taxInvoice, String name, String spec, Integer quantity, Long unitPrice, int sortOrder) {
        long total = unitPrice * quantity;
        long supplyAmount = Math.round(total / 1.1);
        long taxAmount = total - supplyAmount;
        return new TaxInvoiceItem(taxInvoice, name, spec, quantity, unitPrice, (short) sortOrder, supplyAmount, taxAmount);
    }
}
