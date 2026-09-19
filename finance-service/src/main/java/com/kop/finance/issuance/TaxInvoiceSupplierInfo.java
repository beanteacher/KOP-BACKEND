package com.kop.finance.issuance;

/**
 * 세금계산서 공급자(자사) 정보 — finance-service는 auth-service Company를 저장하지 않는다
 * (MSA 서비스 간 직접 참조 금지). 엑셀 생성 시점엔 이미 로그인해서 회사 정보를 갖고 있는
 * 프론트가 요청에 실어 보낸다 — ReceiptSlip 인쇄가 회사 정보를 프론트에서 조합하는 것과 같은 패턴.
 */
public record TaxInvoiceSupplierInfo(String businessRegistrationNumber, String name) {}
