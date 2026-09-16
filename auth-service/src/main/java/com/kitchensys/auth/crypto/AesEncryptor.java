package com.kitchensys.auth.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

/**
 * 09-security.md 예외 조항 — 계좌번호처럼 "DB 접근 권한 자체가 뚫린 상황"(SQL 인젝션, 내부자,
 * DB 관리자 계정 탈취)에서도 평문 노출을 막아야 하는 필드에만 쓰는 앱 레벨 AES-256 암호화다.
 * RDS 스토리지 암호화(SSE-KMS, 09-security.md 저장 시 암호화 절)와는 별개의 방어선이며,
 * 이 필드들은 검색·인덱싱 대상이 아니므로 컬럼 암호화의 단점(등호 검색 불가)이 문제되지 않는다.
 * 새 라이브러리 의존성 없이 spring-boot-starter-security가 가져오는 spring-security-crypto만 쓴다.
 * 현재는 auth-service의 계좌번호에만 쓰여 auth-service 안에 둔다 — 다른 서비스가 필요해지면 그때 common으로 옮긴다.
 */
@Component
public class AesEncryptor {

    private final TextEncryptor encryptor;

    public AesEncryptor(
        @Value("${app.encryption.secret:local-dev-only-change-me-0123456789abcdef}") String secret,
        @Value("${app.encryption.salt:d1b1d1e1f1a1b1c1d1e1f1a1b1c1d1e1}") String salt
    ) {
        this.encryptor = Encryptors.delux(secret, salt);
    }

    public String encrypt(String plaintext) {
        return plaintext == null ? null : encryptor.encrypt(plaintext);
    }

    public String decrypt(String ciphertext) {
        return ciphertext == null ? null : encryptor.decrypt(ciphertext);
    }
}
