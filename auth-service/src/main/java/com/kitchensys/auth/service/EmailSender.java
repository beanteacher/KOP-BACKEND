package com.kitchensys.auth.service;

/**
 * 08-deployment.md에서 운영 메일 발송은 AWS SES로 결정돼 있다. 로컬/개발 환경엔 SES 자격증명이
 * 없으므로 이 인터페이스로 추상화해두고, 운영 배포 시 {@link LoggingEmailSender}를 SES 구현체로
 * 교체한다(Notification 서비스로 옮길 수도 있음 — 04-system-architecture.md 참고, 지금은 Auth가
 * 직접 발송하는 가장 단순한 형태로 시작한다).
 */
public interface EmailSender {
    void send(String to, String subject, String body);
}
