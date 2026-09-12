package com.kitchensys.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 로컬·개발 환경 기본 구현 — 실제 발송 대신 로그로 남긴다. */
@Slf4j
@Component
public class LoggingEmailSender implements EmailSender {

    @Override
    public void send(String to, String subject, String body) {
        log.info("[email:dev-only] to={} subject={} body={}", to, subject, body);
    }
}
