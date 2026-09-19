package com.kop.finance.domain;

/**
 * 거래처(공급받는자)의 과세유형 — 부가세를 별도로 더 받을 수 있는지를 결정한다.
 * GENERAL만 부가세 10%를 별도로 더 받는다(영수증 등록 시 자동 가산). 나머지(SIMPLIFIED/
 * TAX_EXEMPT/NONPROFIT)는 실무상 부가세를 추가로 못 받는 경우를 통칭한 것 — 법적으로 세 유형이
 * 서로 다른 취급을 받을 수 있지만(간이과세자 vs 면세사업자 vs 비영리), 이 시스템에서 "우리가
 * 부가세를 더 못 받는다"는 계산상 효과는 동일해서 하나로 묶어 처리한다(Receipt/TaxInvoiceItem
 * 참고). 세금계산서 발행 시 실제 받은 금액에서 거꾸로 공급가액·세액을 나눠(TaxInvoiceItem.ofInclusive)
 * 우리가 부가세를 떠안는 손실을 장부에 그대로 반영한다.
 */
public enum ClientTaxType {
    GENERAL,
    SIMPLIFIED,
    TAX_EXEMPT,
    NONPROFIT
}
