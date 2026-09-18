-- 03-feature-spec.md 모듈 B(세금계산서), 05-database-schema.md finance.tax_invoices/tax_invoice_items.
-- issue_method는 문서 초안엔 없던 컬럼 — 발행 방식(수동 엑셀 vs 유료 플랜 홈택스 API 자동발행)을
-- TaxInvoiceIssuanceGateway 인터페이스로 분리하기로 한 설계 확정(체크포인트) 반영. 지금은 EXCEL만
-- 실제로 동작하고 HOMETAX_API는 스텁이다.

CREATE TABLE tax_invoices (
  id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  company_id            UUID NOT NULL,                     -- 공급자(자사), FK 없음(MSA 스키마 격리)
  created_by            UUID NOT NULL,
  client_id             UUID NOT NULL REFERENCES clients(id),  -- 공급받는자, 같은 스키마라 실제 FK
  issue_date            DATE NOT NULL,
  status                VARCHAR(20) NOT NULL DEFAULT 'DRAFT',    -- DRAFT/COMPLETED/EXCEL_DOWNLOADED/REVISED/CANCELED
  issue_method          VARCHAR(20) NOT NULL DEFAULT 'EXCEL',    -- EXCEL/HOMETAX_API
  supply_amount         BIGINT NOT NULL DEFAULT 0,
  tax_amount            BIGINT NOT NULL DEFAULT 0,
  total_amount          BIGINT NOT NULL DEFAULT 0,
  note                  VARCHAR(200),
  revised_from_id       UUID REFERENCES tax_invoices(id),  -- 수정 발행 시 원본 참조 (B-4)
  cancel_reason         VARCHAR(100),
  canceled_at           TIMESTAMPTZ,
  excel_downloaded_at   TIMESTAMPTZ,
  created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_tax_invoices_company_issue_date ON tax_invoices(company_id, issue_date DESC);

CREATE TRIGGER trg_tax_invoices_updated_at
  BEFORE UPDATE ON tax_invoices
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE tax_invoice_items (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tax_invoice_id  UUID NOT NULL REFERENCES tax_invoices(id),
  name            VARCHAR(100) NOT NULL,
  spec            VARCHAR(100),
  quantity        INT NOT NULL CHECK (quantity > 0),
  unit_price      BIGINT NOT NULL CHECK (unit_price >= 0),
  supply_amount   BIGINT NOT NULL,      -- quantity * unit_price, 애플리케이션에서 계산 후 저장
  tax_amount      BIGINT NOT NULL,      -- supply_amount * 10%, 애플리케이션에서 계산 후 저장
  sort_order      SMALLINT NOT NULL DEFAULT 0,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 세금계산서 1건당 최대 16개(홈택스 bulk 양식 제한)는 DB 제약이 아니라 애플리케이션에서 검증한다.
CREATE INDEX idx_tax_invoice_items_tax_invoice_id ON tax_invoice_items(tax_invoice_id);

CREATE TRIGGER trg_tax_invoice_items_updated_at
  BEFORE UPDATE ON tax_invoice_items
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();
