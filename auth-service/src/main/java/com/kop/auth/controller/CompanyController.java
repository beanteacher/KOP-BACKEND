package com.kop.auth.controller;

import com.kop.auth.dto.AuthDto;
import com.kop.auth.security.EmployeePrincipal;
import com.kop.auth.service.AuthService;
import com.kop.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** 06-api-design.md 사업자 프로필 조회·수정 — 계좌정보를 포함하므로 관리자 전용. */
@RestController
@RequestMapping("/api/auth/company")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class CompanyController {

    private final AuthService authService;

    @GetMapping
    public ResponseEntity<ApiResponse<AuthDto.CompanyProfileResponse>> get(@AuthenticationPrincipal EmployeePrincipal principal) {
        return ResponseEntity.ok(ApiResponse.of(authService.companyProfile(UUID.fromString(principal.companyId()))));
    }

    @PatchMapping
    public ResponseEntity<ApiResponse<AuthDto.CompanyProfileResponse>> update(
        @AuthenticationPrincipal EmployeePrincipal principal, @RequestBody @Valid AuthDto.CompanyProfileUpdateRequest request
    ) {
        var updated = authService.updateCompanyProfile(UUID.fromString(principal.companyId()), request);
        return ResponseEntity.ok(ApiResponse.of(updated));
    }
}
