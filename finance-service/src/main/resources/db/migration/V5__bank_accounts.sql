-- 오픈뱅킹 연동 계좌 — 입금 확인(영수증 payment_status 매칭)에 쓸 회사 계좌 정보. 회사당 1개.
-- access_token/refresh_token/fintech_use_num은 유출 시 계좌 조회·이체 권한으로 이어지는 민감정보라
-- 앱 레벨 AES-256으로 암호화해 저장한다(common의 AesEncryptor, 09-security.md 예외 조항).
-- bank_name/account_number_masked는 화면 표시용이라 평문(마스킹된 값만 저장, 전체 계좌번호 아님).

CREATE TABLE bank_accounts (
  id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  company_id             UUID NOT NULL UNIQUE,        -- auth.companies.id, FK 없음(MSA 스키마 격리)
  bank_name              VARCHAR(50),
  account_number_masked  VARCHAR(50),
  fintech_use_num        VARCHAR(500) NOT NULL,        -- 암호화 저장(핀테크이용번호)
  access_token           VARCHAR(1000) NOT NULL,       -- 암호화 저장
  refresh_token          VARCHAR(1000) NOT NULL,       -- 암호화 저장
  token_expires_at       TIMESTAMPTZ NOT NULL,
  created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TRIGGER trg_bank_accounts_updated_at
  BEFORE UPDATE ON bank_accounts
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();
