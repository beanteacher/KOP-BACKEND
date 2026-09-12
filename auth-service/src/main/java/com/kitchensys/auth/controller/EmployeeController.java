package com.kitchensys.auth.controller;

import com.kitchensys.auth.dto.EmployeeDto;
import com.kitchensys.auth.security.EmployeePrincipal;
import com.kitchensys.auth.service.EmployeeService;
import com.kitchensys.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** 06-api-design.md 직원 관리·초대 (X-2) — 전부 관리자 전용. */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class EmployeeController {

    private final EmployeeService employeeService;

    @GetMapping("/employees")
    public ResponseEntity<ApiResponse<java.util.List<EmployeeDto.Response>>> list(@AuthenticationPrincipal EmployeePrincipal principal) {
        return ResponseEntity.ok(ApiResponse.of(employeeService.list(UUID.fromString(principal.companyId()))));
    }

    @PatchMapping("/employees/{id}")
    public ResponseEntity<ApiResponse<EmployeeDto.Response>> update(
        @AuthenticationPrincipal EmployeePrincipal principal, @PathVariable UUID id, @RequestBody EmployeeDto.UpdateRequest request
    ) {
        var updated = employeeService.update(UUID.fromString(principal.companyId()), id, request);
        return ResponseEntity.ok(ApiResponse.of(updated));
    }

    @DeleteMapping("/employees/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal EmployeePrincipal principal, @PathVariable UUID id) {
        employeeService.delete(UUID.fromString(principal.companyId()), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/invitations")
    public ResponseEntity<ApiResponse<EmployeeDto.InvitationResponse>> invite(
        @AuthenticationPrincipal EmployeePrincipal principal, @RequestBody @Valid EmployeeDto.InviteRequest request
    ) {
        var invitation = employeeService.invite(UUID.fromString(principal.companyId()), UUID.fromString(principal.employeeId()), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(invitation));
    }

    @GetMapping("/invitations")
    public ResponseEntity<ApiResponse<java.util.List<EmployeeDto.InvitationResponse>>> listInvitations(
        @AuthenticationPrincipal EmployeePrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.of(employeeService.listInvitations(UUID.fromString(principal.companyId()))));
    }

    // 초대 수락은 로그인 전 상태(비로그인)라 이 컨트롤러의 클래스 레벨 @PreAuthorize 대상이 아니다.
    // SecurityConfig에서 "/api/auth/invitations/*/accept"를 permitAll로 열어둔 것과 짝을 맞춘다.
    @PostMapping("/invitations/{token}/accept")
    @PreAuthorize("permitAll()")
    public ResponseEntity<ApiResponse<EmployeeDto.Response>> accept(
        @PathVariable String token, @RequestBody @Valid EmployeeDto.AcceptRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(employeeService.acceptInvitation(token, request)));
    }
}
