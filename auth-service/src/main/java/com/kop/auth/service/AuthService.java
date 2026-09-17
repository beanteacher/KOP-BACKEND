package com.kop.auth.service;

import com.kop.auth.crypto.AesEncryptor;
import com.kop.auth.domain.Company;
import com.kop.auth.domain.Employee;
import com.kop.auth.dto.AuthDto;
import com.kop.auth.event.AuthEventPublisher;
import com.kop.auth.repository.CompanyRepository;
import com.kop.auth.repository.EmployeeRepository;
import com.kop.auth.security.JwtProvider;
import com.kop.auth.security.OneTimeTokenService;
import com.kop.auth.security.RefreshTokenService;
import com.kop.common.exception.BusinessException;
import com.kop.common.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

/** 06-api-design.md Auth API — 회원가입/로그인/토큰 재발급/비밀번호 재설정/내 정보. */
@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private static final Duration EMAIL_VERIFY_TTL = Duration.ofHours(24);
    private static final Duration PASSWORD_RESET_TTL = Duration.ofHours(1);
    private static final String PURPOSE_VERIFY_EMAIL = "verify-email";
    private static final String PURPOSE_PASSWORD_RESET = "password-reset";

    private final CompanyRepository companyRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;
    private final OneTimeTokenService oneTimeTokenService;
    private final EmailSender emailSender;
    private final AuthEventPublisher eventPublisher;
    private final AesEncryptor aesEncryptor;

    /** X-1 업체 계정 생성 — 회사 + 관리자 직원을 한 트랜잭션으로 만든다. */
    public AuthDto.RegisterResponse register(AuthDto.RegisterRequest request) {
        if (companyRepository.existsByBusinessRegistrationNumber(request.businessRegistrationNumber())) {
            throw new BusinessException(HttpStatus.CONFLICT, "BUSINESS_NUMBER_DUPLICATE", "이미 등록된 사업자번호입니다");
        }
        if (employeeRepository.existsByEmail(request.email())) {
            throw new BusinessException(HttpStatus.CONFLICT, "EMAIL_DUPLICATE", "이미 사용 중인 이메일입니다");
        }

        Company company = companyRepository.save(
            Company.register(request.companyName(), request.businessRegistrationNumber(), request.representativeName(), request.phone())
        );
        // 주소·팩스·계좌정보는 선택 입력 — register()의 필수 4개 필드와 분리해 updateProfile로 마저 채운다.
        company.updateProfile(null, request.fax(), request.address(), request.bankName(), request.bankAccountHolder(),
            aesEncryptor.encrypt(request.bankAccountNumber()));
        Employee admin = employeeRepository.save(
            Employee.createAdmin(company, request.email(), passwordEncoder.encode(request.password()), request.representativeName())
        );

        sendVerificationEmail(admin);
        eventPublisher.publishEmployeeRegistered(admin);

        return AuthDto.RegisterResponse.of(company, admin);
    }

    @Transactional(readOnly = true)
    public void requestEmailVerificationResend(String email) {
        Employee employee = employeeRepository.findByEmail(email)
            .orElseThrow(() -> new EntityNotFoundException("직원", email));
        sendVerificationEmail(employee);
    }

    public void verifyEmail(String token) {
        UUID employeeId = oneTimeTokenService.consume(PURPOSE_VERIFY_EMAIL, token)
            .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_TOKEN", "유효하지 않거나 만료된 인증 토큰입니다"));
        Employee employee = employeeRepository.findById(employeeId)
            .orElseThrow(() -> new EntityNotFoundException("직원", employeeId));
        employee.verifyEmail();
    }

    private void sendVerificationEmail(Employee employee) {
        String token = oneTimeTokenService.issue(PURPOSE_VERIFY_EMAIL, employee.getId(), EMAIL_VERIFY_TTL);
        emailSender.send(employee.getEmail(), "이메일 인증",
            "아래 링크로 이메일을 인증해주세요 (24시간 유효): https://app.kop.com/verify-email?token=" + token);
    }

    /** X-3 로그인 — 실패 5회 누적 시 10분 잠금(Employee 엔티티가 직접 추적). */
    public LoginResult login(AuthDto.LoginRequest request) {
        Employee employee = employeeRepository.findByEmail(request.email())
            .orElseThrow(() -> invalidCredentials());

        if (employee.isLocked()) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "ACCOUNT_LOCKED", "로그인 실패가 누적되어 10분간 잠겼습니다");
        }
        if (!employee.isActive() || !passwordEncoder.matches(request.password(), employee.getPasswordHash())) {
            employee.recordFailedLogin();
            throw invalidCredentials();
        }

        employee.resetFailedLogin();
        String accessToken = jwtProvider.issueAccessToken(employee);
        String refreshToken = refreshTokenService.issue(employee.getId(), request.rememberMe());
        return new LoginResult(AuthDto.LoginResponse.of(accessToken, employee), refreshToken, request.rememberMe());
    }

    private BusinessException invalidCredentials() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다");
    }

    @Transactional(readOnly = true)
    public RefreshResult refresh(String refreshToken) {
        RefreshTokenService.ResolvedRefreshToken resolved = refreshTokenService.resolve(refreshToken)
            .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "세션이 만료되었습니다. 다시 로그인해주세요"));
        Employee employee = employeeRepository.findById(resolved.employeeId())
            .orElseThrow(() -> new EntityNotFoundException("직원", resolved.employeeId()));

        // 회전(rotate): 재발급마다 새 Refresh Token을 발급하고 기존 것은 폐기한다. remember는 최초
        // 로그인 때의 선택을 그대로 이어받는다 — 그래야 컨트롤러가 재발급 쿠키의 Max-Age를 로그인
        // 시점과 동일하게(지속 쿠키면 계속 지속, 세션 쿠키면 계속 세션) 유지할 수 있다.
        refreshTokenService.revoke(refreshToken);
        String newRefreshToken = refreshTokenService.issue(employee.getId(), resolved.remember());
        String accessToken = jwtProvider.issueAccessToken(employee);
        return new RefreshResult(new AuthDto.RefreshResponse(accessToken), newRefreshToken, resolved.remember());
    }

    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    @Transactional(readOnly = true)
    public AuthDto.MeResponse me(UUID employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
            .orElseThrow(() -> new EntityNotFoundException("직원", employeeId));
        return AuthDto.MeResponse.from(employee);
    }

    @Transactional(readOnly = true)
    public AuthDto.CompanyProfileResponse companyProfile(UUID companyId) {
        return toProfileResponse(findCompany(companyId));
    }

    /** 관리자 전용 사업자 프로필 수정 — 상호·사업자번호·대표자명은 여기서 바꾸지 않는다. */
    public AuthDto.CompanyProfileResponse updateCompanyProfile(UUID companyId, AuthDto.CompanyProfileUpdateRequest request) {
        Company company = findCompany(companyId);
        company.updateProfile(
            request.phone(), request.fax(), request.address(), request.bankName(), request.bankAccountHolder(),
            request.bankAccountNumber() == null ? null : aesEncryptor.encrypt(request.bankAccountNumber())
        );
        return toProfileResponse(company);
    }

    private Company findCompany(UUID companyId) {
        return companyRepository.findById(companyId)
            .orElseThrow(() -> new EntityNotFoundException("업체", companyId));
    }

    private AuthDto.CompanyProfileResponse toProfileResponse(Company company) {
        return new AuthDto.CompanyProfileResponse(
            company.getId().toString(), company.getName(), company.getBusinessRegistrationNumber(), company.getRepresentativeName(),
            company.getPhone(), company.getFax(), company.getAddress(), company.getBankName(), company.getBankAccountHolder(),
            aesEncryptor.decrypt(company.getBankAccountNumber())
        );
    }

    public void requestPasswordReset(String email) {
        employeeRepository.findByEmail(email).ifPresent(employee -> {
            String token = oneTimeTokenService.issue(PURPOSE_PASSWORD_RESET, employee.getId(), PASSWORD_RESET_TTL);
            emailSender.send(employee.getEmail(), "비밀번호 재설정",
                "아래 링크로 비밀번호를 재설정해주세요 (1시간 유효): https://app.kop.com/reset-password?token=" + token);
        });
        // 존재하지 않는 이메일이어도 같은 응답을 준다 — 계정 존재 여부를 흘리지 않기 위함.
    }

    /** 새 비밀번호 설정 + 기존 모든 세션(Refresh Token) 무효화. */
    public void confirmPasswordReset(String token, String newPassword) {
        UUID employeeId = oneTimeTokenService.consume(PURPOSE_PASSWORD_RESET, token)
            .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_TOKEN", "유효하지 않거나 만료된 토큰입니다"));
        Employee employee = employeeRepository.findById(employeeId)
            .orElseThrow(() -> new EntityNotFoundException("직원", employeeId));

        employee.changePassword(passwordEncoder.encode(newPassword));
        refreshTokenService.revokeAllForEmployee(employeeId);
    }

    public record LoginResult(AuthDto.LoginResponse body, String refreshToken, boolean rememberMe) {}

    public record RefreshResult(AuthDto.RefreshResponse body, String refreshToken, boolean rememberMe) {}
}
