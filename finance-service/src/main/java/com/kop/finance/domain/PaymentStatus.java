package com.kop.finance.domain;

/** 05-database-schema.md receipts.payment_status — 영수증은 매출 청구 문서. 발행 → 입금 확인 → 세금계산서 발행 흐름의 입금 상태. */
public enum PaymentStatus {
    PENDING, PAID, CANCELED
}
