package com.kitchensys.auth.dto;

import com.kitchensys.auth.domain.Company;
import com.kitchensys.auth.domain.Employee;
import jakarta.validation.constraints.*;

/** 06-api-design.md Auth API 요청/응답. backend-conventions.md DTO 규칙 — 도메인당 파일 하나에 inner record. */
public class AuthDto {

    // 길이 상한은 DB 컬럼 최대치(companies.name VARCHAR(100) 등, 05-database-schema.md)를 그대로
    // 쓰지 않고 실사용 기준으로 더 좁혔다 — DB 한도는 "이론상 저장 가능한 최대", 여기는 "정상적인
    // 입력이라면 절대 넘지 않을 값"이라 서로 다른 기준이다. 물론 DB 컬럼보다 넓게 잡으면 안 된다.
    // 비밀번호 72자 상한은 BCrypt가 72바이트를 넘는 입력을 거부하기 때문(PasswordEncoder.encode에서 예외).
    public record RegisterRequest(
        @NotBlank(message = "업체명은 필수입니다") @Size(max = 50, message = "업체명은 50자를 넘을 수 없습니다") String companyName,
        @NotBlank(message = "사업자번호는 필수입니다") @Pattern(regexp = "\\d{10}", message = "사업자번호는 하이픈 없이 10자리 숫자여야 합니다") String businessRegistrationNumber,
        @NotBlank(message = "대표자명은 필수입니다") @Size(max = 30, message = "대표자명은 30자를 넘을 수 없습니다") String representativeName,
        @NotBlank(message = "이메일은 필수입니다") @Email @Size(max = 255) String email,
        @NotBlank(message = "비밀번호는 필수입니다")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$", message = "비밀번호는 8~72자, 영문+숫자 조합이어야 합니다")
        String password,
        @Size(max = 15, message = "연락처는 15자를 넘을 수 없습니다") String phone
    ) {}

    public record RegisterResponse(String companyId, String employeeId, String email, String plan) {
        public static RegisterResponse of(Company company, Employee employee) {
            return new RegisterResponse(
                company.getId().toString(), employee.getId().toString(), employee.getEmail(), company.getPlan().name()
            );
        }
    }

    public record LoginRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 72) String password
    ) {}

    public record LoginResponse(String accessToken, EmployeeSummary employee) {
        public static LoginResponse of(String accessToken, Employee employee) {
            return new LoginResponse(accessToken, EmployeeSummary.from(employee));
        }
    }

    public record EmployeeSummary(String id, String name, String role, String companyId, String plan) {
        public static EmployeeSummary from(Employee employee) {
            return new EmployeeSummary(
                employee.getId().toString(), employee.getName(), employee.getRole().name(),
                employee.getCompanyId().toString(), employee.getCompany().getPlan().name()
            );
        }
    }

    public record RefreshResponse(String accessToken) {}

    public record MeResponse(String id, String name, String email, String role, String companyId, String companyName, String plan) {
        public static MeResponse from(Employee employee) {
            return new MeResponse(
                employee.getId().toString(), employee.getName(), employee.getEmail(), employee.getRole().name(),
                employee.getCompanyId().toString(), employee.getCompany().getName(), employee.getCompany().getPlan().name()
            );
        }
    }

    public record VerifyEmailRequest(@NotBlank String token) {}

    public record PasswordResetRequestRequest(@NotBlank @Email @Size(max = 255) String email) {}

    public record PasswordResetConfirmRequest(
        @NotBlank String token,
        @NotBlank
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$", message = "비밀번호는 8~72자, 영문+숫자 조합이어야 합니다")
        String newPassword
    ) {}
}
