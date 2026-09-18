-- 영수증을 지출 분류 기록이 아니라 매출 청구 문서로 재정의한다: category(재료비/운반비/인건비/
-- 장비비/기타)를 폐기하고, 발행 → 입금 확인 → 세금계산서 자동발행 흐름을 위한 입금 상태를 둔다.
-- matched_transaction_id는 오픈뱅킹 입금 매칭(향후 작업) 결과를 담을 컬럼 — 다른 서비스/스키마
-- 소유 데이터라 FK 없음(MSA 스키마 간 직접 참조 금지, 04-system-architecture.md).

ALTER TABLE receipts ADD COLUMN payment_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';
ALTER TABLE receipts ADD COLUMN paid_at TIMESTAMPTZ;
ALTER TABLE receipts ADD COLUMN matched_transaction_id UUID;

ALTER TABLE receipts DROP COLUMN category;
