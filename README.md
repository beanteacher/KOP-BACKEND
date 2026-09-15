# kop-backend

주방 설비 업체 통합 관리 시스템(KOP) 백엔드 — 설계 문서는 `KOP/` 저장소(00~09번)를 참고한다. 이 저장소는 구현만 담는다.

## 구조

Gradle 멀티 모듈, `04-system-architecture.md` 마이크로서비스 목록과 1:1 대응한다.

| 모듈 | 상태 | 포트 |
|---|---|---|
| `common` | 공통 응답 포맷·예외·이벤트 | - |
| `api-gateway` | 로컬 개발용 라우팅(운영은 AWS API Gateway) | 8080 |
| `auth-service` | 구현됨 — 회원가입/로그인/직원 관리 | 3001 |
| `finance-service` | 골격만 (health check) | 3002 |
| `drawing-service` | 골격만 | 3003 |
| `product-service` | 골격만 | 3004 |
| `notification-service` | 골격만 | 3005 |
| `analytics-service` | 골격만 | 3006 |

## 로컬 실행

```bash
docker compose up -d          # postgres, redis, kafka

./gradlew :auth-service:flywayMigrate   # 최초 1회 — auth 스키마 생성
./gradlew :auth-service:bootRun

# 게이트웨이까지 같이 띄우려면
./gradlew :api-gateway:bootRun
```

`docker compose up` 시 `infra/postgres/init-schemas.sql`이 5개 서비스 스키마(auth/finance/drawings/products/analytics)를 미리 만들어둔다. 각 서비스는 자기 스키마의 Flyway 마이그레이션만 갖는다.

## 빌드·테스트

```bash
./gradlew build
```
