package com.kop.finance.domain;

/** tax_invoices.issue_method — 발행 방식은 TaxInvoiceIssuanceGateway 구현체로 분리된다. */
public enum TaxInvoiceIssueMethod {
    /** 홈택스 bulk 업로드용 엑셀 파일 생성 — 지금 유일하게 동작. */
    EXCEL,
    /** 홈택스 Open API로 직접 발행 — 유료 플랜용, 아직 스텁. */
    HOMETAX_API
}
