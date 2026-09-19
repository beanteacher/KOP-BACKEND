-- 05-database-schema.md 확장: 오픈뱅킹 입금 내역과 영수증을 매칭한 기록(멱등성 원장).
-- 실거래 API는 거래 고유 ID를 주지 않는다(transaction_list 응답 확인 완료) — 같은 입금을
-- 중복 매칭하지 않도록 (company_id, 일시, 금액, 통장인자내용) 조합에 UNIQUE 제약을 건다.
CREATE TABLE matched_bank_transactions (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id            UUID NOT NULL,
    transaction_datetime  TIMESTAMP NOT NULL,
    amount                BIGINT NOT NULL,
    printed_content       VARCHAR(200) NOT NULL,
    matched_receipt_id    UUID NOT NULL REFERENCES receipts(id),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (company_id, transaction_datetime, amount, printed_content)
);

ALTER TABLE receipts
    ADD CONSTRAINT fk_receipts_matched_transaction_id
    FOREIGN KEY (matched_transaction_id) REFERENCES matched_bank_transactions(id);

ALTER TABLE tax_invoices
    ADD COLUMN source_receipt_id UUID REFERENCES receipts(id);

CREATE INDEX idx_tax_invoices_source_receipt_id ON tax_invoices(source_receipt_id);
