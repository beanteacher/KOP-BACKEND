-- 05-database-schema.md § receipt_items — 영수증 1건에 품목(품목명·수량·단가) 여러 개를 담을 수
-- 있도록 확장한다. tax_invoice_items와 같은 패턴. receipts.amount는 이제 사용자가 직접 입력하지
-- 않고 품목 합계를 애플리케이션이 계산해 저장한다(컬럼 자체는 V1과 동일하게 유지).

CREATE TABLE receipt_items (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  receipt_id    UUID NOT NULL REFERENCES receipts(id),
  name          VARCHAR(100) NOT NULL,
  quantity      INT NOT NULL CHECK (quantity > 0),
  unit_price    BIGINT NOT NULL CHECK (unit_price > 0),
  amount        BIGINT NOT NULL,             -- quantity * unit_price, 애플리케이션에서 계산 후 저장
  sort_order    SMALLINT NOT NULL DEFAULT 0,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_receipt_items_receipt_id ON receipt_items(receipt_id);

CREATE TRIGGER trg_receipt_items_updated_at
  BEFORE UPDATE ON receipt_items
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();
