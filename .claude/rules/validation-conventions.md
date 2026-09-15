# 입력 길이 상한 값 (kop-backend)

규칙 자체(왜 필요한지, 상한을 정하는 기준, bcrypt 72바이트 제약)는 `~/workspace/.claude/principles/api-conventions.md`
(스택 무관 공통 Iron Rule)와 `java_backend_workspace/.claude/principles/api-conventions.md`(Java 구체화)를 따른다.
여긴 이 프로젝트에서 실제로 쓰는 값만 둔다.

## 기본값 (새 필드 추가 시 재사용)

| 필드 성격 | 상한 | 적용 예 |
|---|---|---|
| 업체/조직명 | 50 | `companyName` |
| 사람 이름 | 30 | `representativeName`, 초대 수락 `name` |
| 이메일 | 255 | 모든 이메일 필드 |
| 전화번호 | 15 | `phone` |
| 비밀번호(해싱됨) | 8~72 | `password`, `newPassword` |
| 거래처명 | 100 | `ReceiptDto.vendorName` |
| 메모 | 200 | `ReceiptDto.memo` |

새로운 필드 성격(주소, 메모 등)이 나오면 위 표에 추가하고, `05-database-schema.md`의 해당 컬럼 VARCHAR 길이보다 좁게 잡는다. `kop-frontend/.claude/rules/validation-conventions.md`와 항상 동일하게 유지한다.
