package com.kop.auth.dto;

import com.kop.auth.domain.Employee;
import com.kop.auth.domain.EmployeeInvitation;
import com.kop.auth.domain.EmployeeRole;
import com.kop.auth.domain.EmployeeStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** 06-api-design.md 직원 관리·초대 (X-2). */
public class EmployeeDto {

    public record Response(String id, String name, String email, String role, String status) {
        public static Response from(Employee employee) {
            return new Response(
                employee.getId().toString(), employee.getName(), employee.getEmail(),
                employee.getRole().name(), employee.getStatus().name()
            );
        }
    }

    /** role·status 둘 다 선택 — null인 필드는 변경하지 않는다. */
    public record UpdateRequest(EmployeeRole role, EmployeeStatus status) {}

    public record InviteRequest(@NotBlank @Email @Size(max = 255) String email, @NotNull EmployeeRole role) {}

    public record InvitationResponse(String id, String email, String role, String status, Instant expiresAt, Instant createdAt) {
        public static InvitationResponse from(EmployeeInvitation invitation) {
            String status = invitation.isAccepted() ? "ACCEPTED" : invitation.isExpired() ? "EXPIRED" : "PENDING";
            return new InvitationResponse(
                invitation.getId().toString(), invitation.getEmail(), invitation.getRole().name(),
                status, invitation.getExpiresAt(), invitation.getCreatedAt()
            );
        }
    }

    public record AcceptRequest(
        @NotBlank @Size(max = 30, message = "이름은 30자를 넘을 수 없습니다") String name,
        @NotBlank
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$", message = "비밀번호는 8~72자, 영문+숫자 조합이어야 합니다")
        String password
    ) {}
}
