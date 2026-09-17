-- 견적・납품서 인쇄 뷰(규격 컬럼)에 쓰기 위해 품목별 규격을 선택 입력으로 추가한다.
-- 기존 영수증 품목엔 값이 없으므로 NULL 허용, 필수 아님.

ALTER TABLE receipt_items ADD COLUMN spec VARCHAR(100);
