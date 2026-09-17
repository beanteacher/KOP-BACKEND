package com.kop.auth.service;

import com.kop.auth.domain.Company;
import com.kop.auth.domain.Employee;
import com.kop.auth.domain.EmployeeInvitation;
import com.kop.auth.domain.Plan;
import com.kop.auth.dto.EmployeeDto;
import com.kop.auth.event.AuthEventPublisher;
import com.kop.auth.repository.CompanyRepository;
import com.kop.auth.repository.EmployeeInvitationRepository;
import com.kop.auth.repository.EmployeeRepository;
import com.kop.common.exception.BusinessException;
import com.kop.common.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 06-api-design.md 직원 관리·초대 (X-2). 관리자 전용 — 권한 자체는 컨트롤러의 @PreAuthorize가 막는다. */
@Service
@RequiredArgsConstructor
@Transactional
public class EmployeeService {

    // 03-feature-spec.md X-5 — 플랜별 초대 가능 직원 수. Business는 제한 없음(Integer.MAX_VALUE로 표현).
    private static final Map<Plan, Integer> EMPLOYEE_LIMIT = Map.of(
        Plan.FREE, 2, Plan.PRO, 5, Plan.BUSINESS, Integer.MAX_VALUE
    );

    private final CompanyRepository companyRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeInvitationRepository invitationRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<EmployeeDto.Response> list(UUID companyId) {
        return employeeRepository.findByCompany_IdAndDeletedAtIsNull(companyId).stream()
            .map(EmployeeDto.Response::from)
            .toList();
    }

    public EmployeeDto.Response update(UUID companyId, UUID targetEmployeeId, EmployeeDto.UpdateRequest request) {
        Employee employee = getOwnedEmployee(companyId, targetEmployeeId);
        if (request.role() != null) {
            employee.changeRole(request.role());
        }
        if (request.status() != null) {
            switch (request.status()) {
                case ACTIVE -> employee.activate();
                case INACTIVE -> employee.deactivate();
            }
        }
        return EmployeeDto.Response.from(employee);
    }

    public void delete(UUID companyId, UUID targetEmployeeId) {
        getOwnedEmployee(companyId, targetEmployeeId).softDelete();
    }

    private Employee getOwnedEmployee(UUID companyId, UUID employeeId) {
        return employeeRepository.findByIdAndCompany_Id(employeeId, companyId)
            .orElseThrow(() -> new EntityNotFoundException("직원", employeeId));
    }

    public EmployeeDto.InvitationResponse invite(UUID companyId, UUID invitedByEmployeeId, EmployeeDto.InviteRequest request) {
        Company company = companyRepository.findById(companyId)
            .orElseThrow(() -> new EntityNotFoundException("업체", companyId));
        Employee invitedBy = employeeRepository.findById(invitedByEmployeeId)
            .orElseThrow(() -> new EntityNotFoundException("직원", invitedByEmployeeId));

        long currentCount = employeeRepository.findByCompany_IdAndDeletedAtIsNull(companyId).size();
        int limit = EMPLOYEE_LIMIT.get(company.getPlan());
        if (currentCount >= limit) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "EMPLOYEE_PLAN_LIMIT_EXCEEDED",
                "현재 플랜(" + company.getPlan() + ")의 직원 계정 한도(" + limit + "명)를 초과했습니다");
        }
        if (employeeRepository.existsByEmail(request.email())) {
            throw new BusinessException(HttpStatus.CONFLICT, "EMAIL_DUPLICATE", "이미 가입된 이메일입니다");
        }

        // saveAndFlush — InvitationResponse가 바로 createdAt(@CreationTimestamp)을 읽는데,
        // 플러시 전엔 Hibernate가 그 값을 아직 채우지 않는다.
        EmployeeInvitation invitation = invitationRepository.saveAndFlush(
            EmployeeInvitation.create(company, request.email(), request.role(), generateToken(), invitedBy)
        );
        eventPublisher.publishEmployeeInvited(invitation);
        return EmployeeDto.InvitationResponse.from(invitation);
    }

    @Transactional(readOnly = true)
    public List<EmployeeDto.InvitationResponse> listInvitations(UUID companyId) {
        return invitationRepository.findByCompany_IdOrderByCreatedAtDesc(companyId).stream()
            .map(EmployeeDto.InvitationResponse::from)
            .toList();
    }

    public EmployeeDto.Response acceptInvitation(String token, EmployeeDto.AcceptRequest request) {
        EmployeeInvitation invitation = invitationRepository.findByToken(token)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "존재하지 않는 초대 링크입니다"));
        if (invitation.isAccepted()) {
            throw new BusinessException(HttpStatus.CONFLICT, "INVITATION_ALREADY_ACCEPTED", "이미 수락된 초대입니다");
        }
        if (invitation.isExpired()) {
            throw new BusinessException(HttpStatus.GONE, "INVITATION_EXPIRED", "만료된 초대 링크입니다");
        }
        if (employeeRepository.existsByEmail(invitation.getEmail())) {
            throw new BusinessException(HttpStatus.CONFLICT, "EMAIL_DUPLICATE", "이미 가입된 이메일입니다");
        }

        Employee employee = employeeRepository.save(Employee.createFromInvitation(
            invitation.getCompany(), invitation.getEmail(), passwordEncoder.encode(request.password()), request.name(), invitation.getRole()
        ));
        invitation.accept();
        eventPublisher.publishEmployeeRegistered(employee);
        return EmployeeDto.Response.from(employee);
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
