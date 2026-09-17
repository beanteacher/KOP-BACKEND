package com.kop.auth.service;

import com.kop.auth.domain.Company;
import com.kop.auth.domain.Employee;
import com.kop.auth.domain.EmployeeRole;
import com.kop.auth.dto.EmployeeDto;
import com.kop.auth.event.AuthEventPublisher;
import com.kop.auth.repository.CompanyRepository;
import com.kop.auth.repository.EmployeeInvitationRepository;
import com.kop.auth.repository.EmployeeRepository;
import com.kop.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock CompanyRepository companyRepository;
    @Mock EmployeeRepository employeeRepository;
    @Mock EmployeeInvitationRepository invitationRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuthEventPublisher eventPublisher;

    @InjectMocks EmployeeService employeeService;

    private Company company;
    private Employee admin;

    @BeforeEach
    void setUp() {
        company = Company.register("삼진주방설비", "1234567890", "김대표", null);
        ReflectionTestUtils.setField(company, "id", UUID.randomUUID());
        admin = Employee.createAdmin(company, "admin@samjin.co.kr", "hashed", "김대표");
        ReflectionTestUtils.setField(admin, "id", UUID.randomUUID());
    }

    @Test
    void invite_Free_플랜에서_직원이_2명이면_한도초과로_거절한다() {
        Employee staff1 = Employee.createAdmin(company, "s1@samjin.co.kr", "h", "직원1");
        Employee staff2 = Employee.createAdmin(company, "s2@samjin.co.kr", "h", "직원2");
        when(companyRepository.findById(company.getId())).thenReturn(Optional.of(company));
        when(employeeRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(employeeRepository.findByCompany_IdAndDeletedAtIsNull(company.getId()))
            .thenReturn(List.of(admin, staff1, staff2));

        var request = new EmployeeDto.InviteRequest("new@samjin.co.kr", EmployeeRole.STAFF);

        assertThatThrownBy(() -> employeeService.invite(company.getId(), admin.getId(), request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "EMPLOYEE_PLAN_LIMIT_EXCEEDED");
    }

    @Test
    void invite_한도_이내면_초대를_생성하고_이벤트를_발행한다() {
        when(companyRepository.findById(company.getId())).thenReturn(Optional.of(company));
        when(employeeRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(employeeRepository.findByCompany_IdAndDeletedAtIsNull(company.getId())).thenReturn(List.of(admin));
        when(employeeRepository.existsByEmail("new@samjin.co.kr")).thenReturn(false);
        when(invitationRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            var invitation = invocation.getArgument(0, com.kop.auth.domain.EmployeeInvitation.class);
            ReflectionTestUtils.setField(invitation, "id", UUID.randomUUID());
            return invitation;
        });

        var request = new EmployeeDto.InviteRequest("new@samjin.co.kr", EmployeeRole.STAFF);
        EmployeeDto.InvitationResponse response = employeeService.invite(company.getId(), admin.getId(), request);

        assertThat(response.email()).isEqualTo("new@samjin.co.kr");
        assertThat(response.status()).isEqualTo("PENDING");
    }
}
