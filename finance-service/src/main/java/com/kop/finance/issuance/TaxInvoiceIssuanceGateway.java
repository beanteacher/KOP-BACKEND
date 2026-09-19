package com.kop.finance.issuance;

import com.kop.finance.domain.TaxInvoice;

/**
 * 세금계산서 발행 방식을 구현체로 분리하는 포트 — {@link ExcelIssuanceGateway}(지금 유일하게
 * 실제 동작, 홈택스 bulk 업로드 엑셀 생성)만 구현돼 있다. HOMETAX_API(유료 플랜, 홈택스 Open
 * API로 직접 발행)는 아직 구현체가 없다 — {@link com.kop.finance.domain.TaxInvoiceIssueMethod}만
 * 자리를 마련해뒀다.
 */
public interface TaxInvoiceIssuanceGateway {

    /** 홈택스 bulk 업로드용 xlsx 파일 바이트를 만든다. */
    byte[] issue(TaxInvoice taxInvoice, TaxInvoiceSupplierInfo supplier);
}
