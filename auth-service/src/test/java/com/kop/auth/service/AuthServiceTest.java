package com.kop.auth.service;

import com.kop.auth.domain.Company;
import com.kop.auth.domain.Employee;
import com.kop.auth.dto.AuthDto;
import com.kop.auth.event.AuthEventPublisher;
import com.kop.auth.repository.CompanyRepository;
import com.kop.auth.repository.EmployeeRepository;
import com.kop.auth.security.JwtProvider;
import com.kop.auth.security.OneTimeTokenService;
import com.kop.auth.security.RefreshTokenService;
import com.kop.common.crypto.AesEncryptor;
import com.kop.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock CompanyRepository companyRepository;
    @Mock EmployeeRepository employeeRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtProvider jwtProvider;
    @Mock RefreshTokenService refreshTokenService;
    @Mock OneTimeTokenService oneTimeTokenService;
    @Mock EmailSender emailSender;
    @Mock AuthEventPublisher eventPublisher;
    @Mock AesEncryptor aesEncryptor;

    @InjectMocks AuthService authService;

    private Company company;
    private Employee employee;

    @BeforeEach
    void setUp() {
        company = Company.register("삼진주방설비", "1234567890", "김대표", "010-1234-5678");
        ReflectionTestUtils.setField(company, "id", UUID.randomUUID());

        employee = Employee.createAdmin(company, "admin@samjin.co.kr", "hashed", "김대표");
        ReflectionTestUtils.setField(employee, "id", UUID.randomUUID());
    }

    @Test
    void register_주방설비_업체와_관리자_계정을_함께_생성한다() {
        var request = new AuthDto.RegisterRequest(
            "삼진주방설비", "1234567890", "김대표", "admin@samjin.co.kr", "samjin1234", "010-1234-5678",
            null, null, null, null, null
        );
        when(companyRepository.existsByBusinessRegistrationNumber(request.businessRegistrationNumber())).thenReturn(false);
        when(employeeRepository.existsByEmail(request.email())).thenReturn(false);
        when(companyRepository.save(any())).thenReturn(company);
        when(employeeRepository.save(any())).thenReturn(employee);
        when(passwordEncoder.encode(request.password())).thenReturn("hashed");
        when(oneTimeTokenService.issue(any(), any(), any())).thenReturn("verify-token");

        AuthDto.RegisterResponse response = authService.register(request);

        assertThat(response.email()).isEqualTo("admin@samjin.co.kr");
        assertThat(response.plan()).isEqualTo("FREE");
        verify(eventPublisher).publishEmployeeRegistered(employee);
        verify(emailSender).send(eq("admin@samjin.co.kr"), any(), any());
    }

    @Test
    void register_사업자번호가_중복이면_CONFLICT를_던진다() {
        var request = new AuthDto.RegisterRequest(
            "삼진주방설비", "1234567890", "김대표", "admin@samjin.co.kr", "samjin1234", null,
            null, null, null, null, null
        );
        when(companyRepository.existsByBusinessRegistrationNumber(request.businessRegistrationNumber())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "BUSINESS_NUMBER_DUPLICATE");

        verifyNoInteractions(employeeRepository);
    }

    @Test
    void register_선택_입력된_사업자_프로필도_함께_저장하고_계좌번호는_암호화한다() {
        var request = new AuthDto.RegisterRequest(
            "삼진주방설비", "1234567890", "김대표", "admin@samjin.co.kr", "samjin1234", "010-1234-5678",
            "서울시 강남구 테헤란로 1", "02-1234-5678", "국민은행", "김대표", "110-222-333444"
        );
        when(companyRepository.existsByBusinessRegistrationNumber(request.businessRegistrationNumber())).thenReturn(false);
        when(employeeRepository.existsByEmail(request.email())).thenReturn(false);
        when(companyRepository.save(any())).thenReturn(company);
        when(employeeRepository.save(any())).thenReturn(employee);
        when(passwordEncoder.encode(request.password())).thenReturn("hashed");
        when(oneTimeTokenService.issue(any(), any(), any())).thenReturn("verify-token");
        when(aesEncryptor.encrypt("110-222-333444")).thenReturn("ENC(110-222-333444)");

        authService.register(request);

        assertThat(company.getAddress()).isEqualTo("서울시 강남구 테헤란로 1");
        assertThat(company.getFax()).isEqualTo("02-1234-5678");
        assertThat(company.getBankName()).isEqualTo("국민은행");
        assertThat(company.getBankAccountHolder()).isEqualTo("김대표");
        assertThat(company.getBankAccountNumber()).isEqualTo("ENC(110-222-333444)");
    }

    @Test
    void me_회사_사업자정보를_함께_반환한다() {
        when(employeeRepository.findById(employee.getId())).thenReturn(Optional.of(employee));

        AuthDto.MeResponse response = authService.me(employee.getId());

        assertThat(response.businessRegistrationNumber()).isEqualTo("1234567890");
        assertThat(response.representativeName()).isEqualTo("김대표");
        assertThat(response.phone()).isEqualTo("010-1234-5678");
    }

    @Test
    void companyProfile_계좌번호를_복호화해서_반환한다() {
        company.updateProfile(null, "02-1234-5678", "서울시 강남구", "국민은행", "김대표", "ENC(110-222-333444)");
        when(companyRepository.findById(company.getId())).thenReturn(Optional.of(company));
        when(aesEncryptor.decrypt("ENC(110-222-333444)")).thenReturn("110-222-333444");

        AuthDto.CompanyProfileResponse response = authService.companyProfile(company.getId());

        assertThat(response.address()).isEqualTo("서울시 강남구");
        assertThat(response.bankAccountNumber()).isEqualTo("110-222-333444");
    }

    @Test
    void updateCompanyProfile_null_필드는_기존값을_유지하고_계좌번호는_암호화해서_저장한다() {
        company.updateProfile("010-0000-0000", "02-0000-0000", "기존주소", "기존은행", "기존예금주", "OLD_ENC");
        when(companyRepository.findById(company.getId())).thenReturn(Optional.of(company));
        when(aesEncryptor.encrypt("999-888-777666")).thenReturn("NEW_ENC");
        when(aesEncryptor.decrypt("NEW_ENC")).thenReturn("999-888-777666");

        var request = new AuthDto.CompanyProfileUpdateRequest(null, null, "새주소", null, null, "999-888-777666");
        AuthDto.CompanyProfileResponse response = authService.updateCompanyProfile(company.getId(), request);

        assertThat(company.getPhone()).isEqualTo("010-0000-0000");
        assertThat(company.getFax()).isEqualTo("02-0000-0000");
        assertThat(company.getAddress()).isEqualTo("새주소");
        assertThat(response.bankAccountNumber()).isEqualTo("999-888-777666");
    }

    @Test
    void login_비밀번호가_틀리면_실패카운트를_올리고_INVALID_CREDENTIALS를_던진다() {
        var request = new AuthDto.LoginRequest("admin@samjin.co.kr", "wrong-password", false);
        when(employeeRepository.findByEmail(request.email())).thenReturn(Optional.of(employee));
        when(passwordEncoder.matches(request.password(), employee.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "INVALID_CREDENTIALS");

        assertThat(employee.getFailedLoginCount()).isEqualTo((short) 1);
    }

    @Test
    void login_5회_연속_실패하면_잠긴다() {
        var request = new AuthDto.LoginRequest("admin@samjin.co.kr", "wrong-password", false);
        when(employeeRepository.findByEmail(request.email())).thenReturn(Optional.of(employee));
        when(passwordEncoder.matches(any(), any())).thenReturn(false);

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> authService.login(request)).isInstanceOf(BusinessException.class);
        }

        assertThat(employee.isLocked()).isTrue();
    }

    @Test
    void login_잠긴_계정은_비밀번호가_맞아도_ACCOUNT_LOCKED를_던진다() {
        for (int i = 0; i < 5; i++) {
            employee.recordFailedLogin();
        }
        var request = new AuthDto.LoginRequest("admin@samjin.co.kr", "samjin1234", false);
        when(employeeRepository.findByEmail(request.email())).thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> authService.login(request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "ACCOUNT_LOCKED");
    }

    @Test
    void login_성공하면_실패카운트가_초기화되고_토큰이_발급된다() {
        var request = new AuthDto.LoginRequest("admin@samjin.co.kr", "samjin1234", false);
        employee.recordFailedLogin();
        when(employeeRepository.findByEmail(request.email())).thenReturn(Optional.of(employee));
        when(passwordEncoder.matches(request.password(), employee.getPasswordHash())).thenReturn(true);
        when(jwtProvider.issueAccessToken(employee)).thenReturn("access-token");
        when(refreshTokenService.issue(employee.getId(), false)).thenReturn("refresh-token");

        AuthService.LoginResult result = authService.login(request);

        assertThat(result.body().accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(result.rememberMe()).isFalse();
        assertThat(employee.getFailedLoginCount()).isEqualTo((short) 0);
    }

    @Test
    void login_로그인상태유지를_체크하면_remember가_true인_토큰을_발급한다() {
        var request = new AuthDto.LoginRequest("admin@samjin.co.kr", "samjin1234", true);
        when(employeeRepository.findByEmail(request.email())).thenReturn(Optional.of(employee));
        when(passwordEncoder.matches(request.password(), employee.getPasswordHash())).thenReturn(true);
        when(jwtProvider.issueAccessToken(employee)).thenReturn("access-token");
        when(refreshTokenService.issue(employee.getId(), true)).thenReturn("refresh-token");

        AuthService.LoginResult result = authService.login(request);

        assertThat(result.rememberMe()).isTrue();
        verify(refreshTokenService).issue(employee.getId(), true);
    }

    @Test
    void refresh_최초_로그인의_remember_선택을_재발급_토큰에도_그대로_이어간다() {
        UUID employeeId = employee.getId();
        when(refreshTokenService.resolve("old-token"))
            .thenReturn(Optional.of(new RefreshTokenService.ResolvedRefreshToken(employeeId, true)));
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(jwtProvider.issueAccessToken(employee)).thenReturn("new-access-token");
        when(refreshTokenService.issue(employeeId, true)).thenReturn("new-refresh-token");

        AuthService.RefreshResult result = authService.refresh("old-token");

        assertThat(result.body().accessToken()).isEqualTo("new-access-token");
        assertThat(result.refreshToken()).isEqualTo("new-refresh-token");
        assertThat(result.rememberMe()).isTrue();
        verify(refreshTokenService).revoke("old-token");
    }

    @Test
    void confirmPasswordReset_비밀번호_변경_후_모든_세션을_무효화한다() {
        UUID employeeId = employee.getId();
        when(oneTimeTokenService.consume("password-reset", "reset-token")).thenReturn(Optional.of(employeeId));
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(passwordEncoder.encode("newpass123")).thenReturn("new-hash");

        authService.confirmPasswordReset("reset-token", "newpass123");

        assertThat(employee.getPasswordHash()).isEqualTo("new-hash");
        verify(refreshTokenService).revokeAllForEmployee(employeeId);
    }
}
