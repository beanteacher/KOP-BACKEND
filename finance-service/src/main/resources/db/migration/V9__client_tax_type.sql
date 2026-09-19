-- 거래처 과세유형(간이과세자/면세사업자/비영리 등은 부가세를 별도로 못 받음) — 세금계산서
-- 자동발행(TaxInvoiceAutoIssuanceService)이 참고한다. 영수증 금액 자체는 그대로 실제
-- 청구·입금 총액(부가세 포함 여부와 무관하게 등록자가 합의된 실제 금액을 입력)이라 손대지 않는다.
ALTER TABLE clients ADD COLUMN tax_type VARCHAR(20) NOT NULL DEFAULT 'GENERAL';
