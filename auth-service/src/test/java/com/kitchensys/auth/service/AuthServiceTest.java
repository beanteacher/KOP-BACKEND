package com.kitchensys.auth.service;

import com.kitchensys.auth.domain.Company;
import com.kitchensys.auth.domain.Employee;
import com.kitchensys.auth.dto.AuthDto;
import com.kitchensys.auth.event.AuthEventPublisher;
import com.kitchensys.auth.repository.CompanyRepository;
import com.kitchensys.auth.repository.EmployeeRepository;
import com.kitchensys.auth.security.JwtProvider;
import com.kitchensys.auth.security.OneTimeTokenService;
import com.kitchensys.auth.security.RefreshTokenService;
import com.kitchensys.common.exception.BusinessException;
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
        var request = new AuthDto.RegisterRequest("삼진주방설비", "1234567890", "김대표", "admin@samjin.co.kr", "samjin1234", "010-1234-5678");
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
        var request = new AuthDto.RegisterRequest("삼진주방설비", "1234567890", "김대표", "admin@samjin.co.kr", "samjin1234", null);
        when(companyRepository.existsByBusinessRegistrationNumber(request.businessRegistrationNumber())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "BUSINESS_NUMBER_DUPLICATE");

        verifyNoInteractions(employeeRepository);
    }

    @Test
    void login_비밀번호가_틀리면_실패카운트를_올리고_INVALID_CREDENTIALS를_던진다() {
        var request = new AuthDto.LoginRequest("admin@samjin.co.kr", "wrong-password");
        when(employeeRepository.findByEmail(request.email())).thenReturn(Optional.of(employee));
        when(passwordEncoder.matches(request.password(), employee.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "INVALID_CREDENTIALS");

        assertThat(employee.getFailedLoginCount()).isEqualTo((short) 1);
    }

    @Test
    void login_5회_연속_실패하면_잠긴다() {
        var request = new AuthDto.LoginRequest("admin@samjin.co.kr", "wrong-password");
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
        var request = new AuthDto.LoginRequest("admin@samjin.co.kr", "samjin1234");
        when(employeeRepository.findByEmail(request.email())).thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> authService.login(request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "ACCOUNT_LOCKED");
    }

    @Test
    void login_성공하면_실패카운트가_초기화되고_토큰이_발급된다() {
        var request = new AuthDto.LoginRequest("admin@samjin.co.kr", "samjin1234");
        employee.recordFailedLogin();
        when(employeeRepository.findByEmail(request.email())).thenReturn(Optional.of(employee));
        when(passwordEncoder.matches(request.password(), employee.getPasswordHash())).thenReturn(true);
        when(jwtProvider.issueAccessToken(employee)).thenReturn("access-token");
        when(refreshTokenService.issue(employee.getId())).thenReturn("refresh-token");

        AuthService.LoginResult result = authService.login(request);

        assertThat(result.body().accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(employee.getFailedLoginCount()).isEqualTo((short) 0);
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
