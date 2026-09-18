package com.kop.finance.controller;

import com.kop.common.response.ApiResponse;
import com.kop.finance.dto.BankAccountDto;
import com.kop.finance.security.EmployeePrincipal;
import com.kop.finance.service.BankAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 06-api-design.md Finance API — 오픈뱅킹 계좌 연결 `/api/bank-accounts`. 관리자 전용. */
@RestController
@RequestMapping("/api/bank-accounts")
@RequiredArgsConstructor
public class BankAccountController {

    private final BankAccountService bankAccountService;

    @GetMapping("/authorize-url")
    public ResponseEntity<ApiResponse<BankAccountDto.AuthorizeUrlResponse>> authorizeUrl(
        @AuthenticationPrincipal EmployeePrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.of(bankAccountService.authorizeUrl(principal)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BankAccountDto.Response>> register(
        @AuthenticationPrincipal EmployeePrincipal principal, @RequestBody @Valid BankAccountDto.RegisterRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.of(bankAccountService.register(principal, request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<BankAccountDto.Response>> status(@AuthenticationPrincipal EmployeePrincipal principal) {
        return ResponseEntity.ok(ApiResponse.of(bankAccountService.getStatus(principal)));
    }
}
