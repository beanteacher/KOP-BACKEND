package com.kop.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 로컬·개발 환경 기본 구현 — 실제 발송 대신 로그로 남긴다. email.provider가 없거나 "logging"이면
 * 이 빈이 활성화된다(matchIfMissing=true) — AWS 자격증명 없이도 로컬 개발이 그대로 동작해야 해서다.
 * 운영은 email.provider=ses로 {@link SesEmailSender} 를 대신 활성화한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "email.provider", havingValue = "logging", matchIfMissing = true)
public class LoggingEmailSender implements EmailSender {

    @Override
    public void send(String to, String subject, String body) {
        log.info("[email:dev-only] to={} subject={} body={}", to, subject, body);
    }
}
