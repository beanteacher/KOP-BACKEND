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
        @Size(max = 15, message = "연락처는 15자를 넘을 수 없습니다") String phone,
        @Size(max = 200, message = "주소는 200자를 넘을 수 없습니다") String address,
        @Size(max = 15, message = "팩스번호는 15자를 넘을 수 없습니다") String fax,
        @Size(max = 50, message = "은행명은 50자를 넘을 수 없습니다") String bankName,
        @Size(max = 50, message = "예금주명은 50자를 넘을 수 없습니다") String bankAccountHolder,
        @Size(max = 50, message = "계좌번호는 50자를 넘을 수 없습니다") String bankAccountNumber
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
        @NotBlank @Size(max = 72) String password,
        boolean rememberMe
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

    /**
     * businessRegistrationNumber·representativeName·phone·address는 인쇄용 영수증(ReceiptSlip) 헤더에
     * 필요해서 여기 포함한다. 계좌정보(bankName 등)는 여기 넣지 않는다 — 모든 로그인 사용자(직원 포함)가
     * 매 로드마다 받는 응답이라 회사 계좌번호까지 노출할 필요는 없다. 계좌정보는 관리자 전용
     * CompanyProfileResponse(GET/PATCH /api/auth/company)로만 조회한다.
     */
    public record MeResponse(
        String id, String name, String email, String role, String companyId, String companyName, String plan,
        String businessRegistrationNumber, String representativeName, String phone, String address
    ) {
        public static MeResponse from(Employee employee) {
            Company company = employee.getCompany();
            return new MeResponse(
                employee.getId().toString(), employee.getName(), employee.getEmail(), employee.getRole().name(),
                employee.getCompanyId().toString(), company.getName(), company.getPlan().name(),
                company.getBusinessRegistrationNumber(), company.getRepresentativeName(), company.getPhone(), company.getAddress()
            );
        }
    }

    /** 관리자 전용 사업자 프로필 조회/수정 응답 (GET·PATCH /api/auth/company) — 계좌번호는 복호화된 평문. */
    public record CompanyProfileResponse(
        String id, String name, String businessRegistrationNumber, String representativeName, String phone,
        String fax, String address, String bankName, String bankAccountHolder, String bankAccountNumber
    ) {}

    /**
     * 전화·팩스·주소·계좌정보만 수정 가능 — 상호·사업자번호·대표자명은 이 엔드포인트로 바꾸지 않는다.
     * EmployeeDto.UpdateRequest와 같은 규칙: null인 필드는 변경하지 않는다(빈 문자열 ""은 지우는 것으로 처리됨).
     */
    public record CompanyProfileUpdateRequest(
        @Size(max = 15, message = "연락처는 15자를 넘을 수 없습니다") String phone,
        @Size(max = 15, message = "팩스번호는 15자를 넘을 수 없습니다") String fax,
        @Size(max = 200, message = "주소는 200자를 넘을 수 없습니다") String address,
        @Size(max = 50, message = "은행명은 50자를 넘을 수 없습니다") String bankName,
        @Size(max = 50, message = "예금주명은 50자를 넘을 수 없습니다") String bankAccountHolder,
        @Size(max = 50, message = "계좌번호는 50자를 넘을 수 없습니다") String bankAccountNumber
    ) {}

    public record VerifyEmailRequest(@NotBlank String token) {}

    public record PasswordResetRequestRequest(@NotBlank @Email @Size(max = 255) String email) {}

    public record PasswordResetConfirmRequest(
        @NotBlank String token,
        @NotBlank
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$", message = "비밀번호는 8~72자, 영문+숫자 조합이어야 합니다")
        String newPassword
    ) {}
}
