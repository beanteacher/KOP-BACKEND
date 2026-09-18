-- 03-feature-spec.md B-1, 05-database-schema.md finance.clients — 세금계산서 공급받는자 주소록.
-- receipts.client_id는 V1부터 있었지만 clients 테이블이 없어 FK를 못 걸었다("finance.clients.id
-- (거래처 주소록, 아직 미구현)") — 이제 같은 스키마 안이라 진짜 FK로 건다(receipt_items와 같은 원칙).

CREATE TABLE clients (
  id                             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  company_id                     UUID NOT NULL,          -- auth.companies.id, FK 없음(MSA 스키마 격리)
  business_registration_number   VARCHAR(10) NOT NULL,
  name                           VARCHAR(100) NOT NULL,
  representative_name            VARCHAR(50) NOT NULL,
  business_type                  VARCHAR(100) NOT NULL,  -- 업태
  business_item                  VARCHAR(100) NOT NULL,  -- 종목
  address                        VARCHAR(255) NOT NULL,
  email                          VARCHAR(255),
  contact_name                   VARCHAR(50),
  contact_phone                  VARCHAR(20),
  status                         VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE/INACTIVE
  created_at                     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at                     TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (company_id, business_registration_number)
);

CREATE INDEX idx_clients_company_brn ON clients(company_id, business_registration_number);

CREATE TRIGGER trg_clients_updated_at
  BEFORE UPDATE ON clients
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE receipts ADD CONSTRAINT fk_receipts_client_id FOREIGN KEY (client_id) REFERENCES clients(id);
