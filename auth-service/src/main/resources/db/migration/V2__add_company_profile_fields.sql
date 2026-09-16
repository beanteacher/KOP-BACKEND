-- 사업자 프로필 확장 — 인쇄용 영수증에 사업장 주소를 표시하고(06-api-design.md GET /api/auth/me),
-- 팩스·계좌정보를 회원가입 후에도 수정 가능하게 한다(PATCH /api/auth/company).
-- bank_account_number는 애플리케이션 레벨 AES 암호화(AesEncryptor)로 암호문을 저장하므로
-- 평문 계좌번호보다 훨씬 긴 컬럼이 필요하다 — 09-security.md 예외 조항 참고.
ALTER TABLE companies
  ADD COLUMN address              VARCHAR(200),
  ADD COLUMN fax                  VARCHAR(20),
  ADD COLUMN bank_name            VARCHAR(50),
  ADD COLUMN bank_account_holder  VARCHAR(50),
  ADD COLUMN bank_account_number  VARCHAR(255);
