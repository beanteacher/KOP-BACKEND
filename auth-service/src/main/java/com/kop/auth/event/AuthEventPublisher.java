package com.kop.auth.event;

import com.kop.auth.domain.Employee;
import com.kop.auth.domain.EmployeeInvitation;
import com.kop.common.event.DomainEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 04-system-architecture.md Kafka 토픽 목록 — employee.registered(→ Analytics),
 * employee.invited(→ Notification, 실제 메일 발송은 Notification Service 구현 시 소비한다).
 * 지금은 Outbox 패턴 없이 직접 발행한다 — Outbox는 Finance/Drawing처럼 파일·결제가 얽힌
 * 서비스부터 적용하고, Auth는 이벤트 유실 시 재가입·재초대로 복구 가능한 낮은 리스크라 뒤로 미룬다.
 */
@Component
@RequiredArgsConstructor
public class AuthEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishEmployeeRegistered(Employee employee) {
        var payload = new EmployeeRegisteredPayload(
            employee.getId().toString(), employee.getCompanyId().toString(), employee.getEmail(), employee.getName()
        );
        kafkaTemplate.send("employee.registered", employee.getCompanyId().toString(),
            DomainEvent.of("employee.registered", employee.getCompanyId().toString(), payload));
    }

    public void publishEmployeeInvited(EmployeeInvitation invitation) {
        var payload = new EmployeeInvitedPayload(
            invitation.getId().toString(), invitation.getEmail(), invitation.getRole().name(), invitation.getToken()
        );
        kafkaTemplate.send("employee.invited", invitation.getCompanyId().toString(),
            DomainEvent.of("employee.invited", invitation.getCompanyId().toString(), payload));
    }

    public record EmployeeRegisteredPayload(String employeeId, String companyId, String email, String name) {}

    public record EmployeeInvitedPayload(String invitationId, String email, String role, String token) {}
}
