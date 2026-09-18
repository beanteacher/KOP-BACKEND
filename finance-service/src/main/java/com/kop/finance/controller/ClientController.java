package com.kop.finance.controller;

import com.kop.common.response.ApiResponse;
import com.kop.common.response.PageMeta;
import com.kop.finance.dto.ClientDto;
import com.kop.finance.security.EmployeePrincipal;
import com.kop.finance.service.ClientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** 06-api-design.md Finance API — 거래처 `/api/clients`. 전부 관리자 전용. */
@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;

    @PostMapping
    public ResponseEntity<ApiResponse<ClientDto.Response>> register(
        @AuthenticationPrincipal EmployeePrincipal principal, @RequestBody @Valid ClientDto.RegisterRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(clientService.register(principal, request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Iterable<ClientDto.ListItem>>> list(
        @AuthenticationPrincipal EmployeePrincipal principal,
        @RequestParam(required = false) String query,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Page<ClientDto.ListItem> result = clientService.list(principal, query, status, PageRequest.of(page - 1, size));
        return ResponseEntity.ok(ApiResponse.of(result.getContent(), PageMeta.from(result)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ClientDto.Response>> getDetail(
        @AuthenticationPrincipal EmployeePrincipal principal, @PathVariable UUID id
    ) {
        return ResponseEntity.ok(ApiResponse.of(clientService.getDetail(principal, id)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<ClientDto.Response>> update(
        @AuthenticationPrincipal EmployeePrincipal principal, @PathVariable UUID id, @RequestBody @Valid ClientDto.UpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.of(clientService.update(principal, id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@AuthenticationPrincipal EmployeePrincipal principal, @PathVariable UUID id) {
        clientService.delete(principal, id);
        return ResponseEntity.ok(ApiResponse.of(null));
    }
}
