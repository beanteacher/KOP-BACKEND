-- 05-database-schema.md § auth 스키마. 공통 규칙(공통 절 참고): UUID PK, created_at/updated_at,
-- company_id로 테넌트 스코프. auth 스키마는 Flyway application.yml의 default-schema로 지정되어
-- 있으므로 테이블명에 스키마 접두사를 붙이지 않는다.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE companies (
  id                             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name                           VARCHAR(100) NOT NULL,
  business_registration_number  VARCHAR(10) NOT NULL UNIQUE,
  representative_name            VARCHAR(50) NOT NULL,
  phone                           VARCHAR(20),
  plan                            VARCHAR(20) NOT NULL DEFAULT 'FREE',
  plan_updated_at                 TIMESTAMPTZ,
  created_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at                      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TRIGGER trg_companies_updated_at
  BEFORE UPDATE ON companies
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE employees (
  id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  company_id           UUID NOT NULL REFERENCES companies(id),
  email                VARCHAR(255) NOT NULL UNIQUE,
  password_hash        VARCHAR(255) NOT NULL,
  name                 VARCHAR(50) NOT NULL,
  role                 VARCHAR(20) NOT NULL,               -- ADMIN / STAFF
  status               VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE / INACTIVE
  email_verified_at    TIMESTAMPTZ,
  failed_login_count   SMALLINT NOT NULL DEFAULT 0,
  locked_until         TIMESTAMPTZ,
  deleted_at           TIMESTAMPTZ,
  created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_employees_company_id ON employees(company_id);

CREATE TRIGGER trg_employees_updated_at
  BEFORE UPDATE ON employees
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE employee_invitations (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  company_id    UUID NOT NULL REFERENCES companies(id),
  email         VARCHAR(255) NOT NULL,
  role          VARCHAR(20) NOT NULL,
  token         VARCHAR(255) NOT NULL UNIQUE,
  invited_by    UUID NOT NULL REFERENCES employees(id),
  expires_at    TIMESTAMPTZ NOT NULL,
  accepted_at   TIMESTAMPTZ,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_employee_invitations_company_id ON employee_invitations(company_id);

CREATE TRIGGER trg_employee_invitations_updated_at
  BEFORE UPDATE ON employee_invitations
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();
