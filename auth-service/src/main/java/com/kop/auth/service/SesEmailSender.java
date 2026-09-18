package com.kop.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SesException;

/**
 * 08-deployment.md 운영 메일 발송 — AWS SES. AWS 자격증명은 SDK 기본 체인을 그대로 쓴다
 * (환경변수 AWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEY, ~/.aws/credentials, 운영은 ECS
 * Task Role 등 IAM Role) — 이 클래스가 따로 받지 않는다. from-address는 SES에 검증된
 * (verified) 이메일/도메인이어야 한다 — 미검증이면 send() 시점에 SES가 거부한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "email.provider", havingValue = "ses")
public class SesEmailSender implements EmailSender {

    private final SesClient sesClient;
    private final String fromAddress;

    public SesEmailSender(
        @Value("${email.ses.region}") String region,
        @Value("${email.ses.from-address}") String fromAddress
    ) {
        this(SesClient.builder().region(Region.of(region)).build(), fromAddress);
    }

    /** SesClient를 직접 주입하는 생성자 — 테스트에서 목(mock)으로 대체하기 위함. */
    SesEmailSender(SesClient sesClient, String fromAddress) {
        if (fromAddress == null || fromAddress.isBlank()) {
            throw new IllegalStateException(
                "email.ses.from-address(EMAIL_FROM_ADDRESS)가 설정되지 않았습니다 — SES에 검증된 발신 주소가 필요합니다."
            );
        }
        this.sesClient = sesClient;
        this.fromAddress = fromAddress;
    }

    @Override
    public void send(String to, String subject, String body) {
        SendEmailRequest request = SendEmailRequest.builder()
            .source(fromAddress)
            .destination(Destination.builder().toAddresses(to).build())
            .message(Message.builder()
                .subject(Content.builder().data(subject).charset("UTF-8").build())
                .body(Body.builder().text(Content.builder().data(body).charset("UTF-8").build()).build())
                .build())
            .build();

        try {
            sesClient.sendEmail(request);
        } catch (SesException e) {
            log.error("SES 메일 발송 실패: to={} subject={}", to, subject, e);
            throw e;
        }
    }
}
