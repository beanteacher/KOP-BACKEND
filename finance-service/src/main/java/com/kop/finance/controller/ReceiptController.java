package com.kop.finance.controller;

import com.kop.common.response.ApiResponse;
import com.kop.common.response.PageMeta;
import com.kop.finance.dto.ReceiptDto;
import com.kop.finance.security.EmployeePrincipal;
import com.kop.finance.service.ReceiptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

/** 06-api-design.md Finance API — 영수증 `/api/receipts`. */
@RestController
@RequestMapping("/api/receipts")
@RequiredArgsConstructor
public class ReceiptController {

    private final ReceiptService receiptService;

    @PostMapping
    public ResponseEntity<ApiResponse<ReceiptDto.Response>> register(
        @AuthenticationPrincipal EmployeePrincipal principal, @RequestBody @Valid ReceiptDto.RegisterRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(receiptService.register(principal, request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Iterable<ReceiptDto.ListItem>>> list(
        @AuthenticationPrincipal EmployeePrincipal principal,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) String clientId,
        @RequestParam(required = false) String paymentStatus,
        @RequestParam(required = false) String createdBy,
        @RequestParam(required = false) String vendorName,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        // A-2 기본값 — 기간 미지정 시 "이번 달 1일~오늘".
        LocalDate today = LocalDate.now();
        LocalDate effectiveFrom = from != null ? from : today.withDayOfMonth(1);
        LocalDate effectiveTo = to != null ? to : today;

        ReceiptService.ListResult result = receiptService.list(
            principal, effectiveFrom, effectiveTo, clientId, paymentStatus, createdBy, vendorName, PageRequest.of(page - 1, size)
        );
        Page<ReceiptDto.ListItem> items = result.receipts().map(ReceiptDto.ListItem::from);
        return ResponseEntity.ok(ApiResponse.of(items.getContent(), PageMeta.from(items, result.totalAmount())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ReceiptDto.Response>> getDetail(
        @AuthenticationPrincipal EmployeePrincipal principal, @PathVariable UUID id
    ) {
        return ResponseEntity.ok(ApiResponse.of(receiptService.getDetail(principal, id)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<ReceiptDto.Response>> update(
        @AuthenticationPrincipal EmployeePrincipal principal, @PathVariable UUID id, @RequestBody @Valid ReceiptDto.UpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.of(receiptService.update(principal, id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@AuthenticationPrincipal EmployeePrincipal principal, @PathVariable UUID id) {
        receiptService.delete(principal, id);
        return ResponseEntity.ok(ApiResponse.of(null));
    }

    @PostMapping("/{id}/mark-paid")
    public ResponseEntity<ApiResponse<ReceiptDto.Response>> markPaid(
        @AuthenticationPrincipal EmployeePrincipal principal, @PathVariable UUID id
    ) {
        return ResponseEntity.ok(ApiResponse.of(receiptService.markPaid(principal, id)));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<ReceiptDto.Response>> cancel(
        @AuthenticationPrincipal EmployeePrincipal principal, @PathVariable UUID id
    ) {
        return ResponseEntity.ok(ApiResponse.of(receiptService.cancel(principal, id)));
    }

    /** 관리자 전용 — 오픈뱅킹 입금 내역을 조회해 PENDING 영수증과 자동 매칭한다(입금 매칭 즉시 실행). */
    @PostMapping("/match-payments")
    public ResponseEntity<ApiResponse<ReceiptDto.MatchPaymentsResponse>> matchPayments(@AuthenticationPrincipal EmployeePrincipal principal) {
        return ResponseEntity.ok(ApiResponse.of(ReceiptDto.MatchPaymentsResponse.from(receiptService.matchPayments(principal))));
    }
}
