package com.kop.auth.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SesException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SesEmailSenderTest {

    @Mock SesClient sesClient;

    @Test
    void send하면_from_to_subject_body를_담아_SES를_호출한다() {
        SesEmailSender sender = new SesEmailSender(sesClient, "no-reply@kop.com");

        sender.send("user@example.com", "비밀번호 재설정", "링크: https://app.kop.com/reset-password?token=abc");

        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesClient).sendEmail(captor.capture());
        SendEmailRequest request = captor.getValue();

        assertThat(request.source()).isEqualTo("no-reply@kop.com");
        assertThat(request.destination().toAddresses()).containsExactly("user@example.com");
        assertThat(request.message().subject().data()).isEqualTo("비밀번호 재설정");
        assertThat(request.message().body().text().data()).isEqualTo("링크: https://app.kop.com/reset-password?token=abc");
    }

    @Test
    void SES가_예외를_던지면_그대로_전파한다() {
        SesEmailSender sender = new SesEmailSender(sesClient, "no-reply@kop.com");
        when(sesClient.sendEmail((SendEmailRequest) org.mockito.ArgumentMatchers.any()))
            .thenThrow(SesException.builder().message("발신 주소 미검증").build());

        assertThatThrownBy(() -> sender.send("user@example.com", "제목", "본문"))
            .isInstanceOf(SesException.class);
    }

    @Test
    void from_address가_빈_문자열이면_생성_시점에_예외를_던진다() {
        assertThatThrownBy(() -> new SesEmailSender(sesClient, ""))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void from_address가_null이면_생성_시점에_예외를_던진다() {
        assertThatThrownBy(() -> new SesEmailSender(sesClient, null))
            .isInstanceOf(IllegalStateException.class);
    }
}
