-- 05-database-schema.md § finance 스키마 — 이번 라운드는 receipts만. clients/tax_invoices/
-- receipt_images는 해당 기능(거래처 관리, 세금계산서, 이미지 첨부) 구현 시 별도 마이그레이션으로 추가한다.
-- 공통 규칙: UUID PK, created_at/updated_at, company_id로 테넌트 스코프. finance 스키마는
-- Flyway application.yml의 default-schema라 테이블명에 스키마 접두사를 붙이지 않는다.
-- auth 스키마와 마찬가지로 set_updated_at()을 자체적으로 둔다 — MSA 스키마 간 직접 참조 금지 원칙
-- (04-system-architecture.md)상 auth 스키마의 함수를 공유하지 않는다.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE receipts (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  company_id    UUID NOT NULL,                  -- auth.companies.id, FK 없음(MSA 스키마 격리)
  created_by    UUID NOT NULL,                  -- auth.employees.id, FK 없음
  receipt_date  DATE NOT NULL,
  amount        BIGINT NOT NULL CHECK (amount > 0),
  vendor_name   VARCHAR(100) NOT NULL,
  client_id     UUID,                           -- finance.clients.id (거래처 주소록, 아직 미구현)
  category      VARCHAR(20) NOT NULL,           -- MATERIAL/TRANSPORT/LABOR/EQUIPMENT/OTHER
  memo          VARCHAR(200),
  deleted_at    TIMESTAMPTZ,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 인덱스 전략(05-database-schema.md): 기간 필터+최신순 목록, 직원 본인 등록건 조회.
CREATE INDEX idx_receipts_company_date ON receipts(company_id, receipt_date DESC);
CREATE INDEX idx_receipts_company_created_by ON receipts(company_id, created_by);

CREATE TRIGGER trg_receipts_updated_at
  BEFORE UPDATE ON receipts
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();
