-- 04-system-architecture.md "단일 RDS 인스턴스 + 서비스별 스키마 분리"를 로컬 docker-compose에도
-- 그대로 반영한다. 각 서비스의 Flyway가 자기 스키마 안의 테이블만 만든다.
CREATE SCHEMA IF NOT EXISTS auth;
CREATE SCHEMA IF NOT EXISTS finance;
CREATE SCHEMA IF NOT EXISTS drawings;
CREATE SCHEMA IF NOT EXISTS products;
CREATE SCHEMA IF NOT EXISTS analytics;
