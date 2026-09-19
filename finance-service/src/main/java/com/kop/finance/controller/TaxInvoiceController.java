package com.kop.finance.controller;

import com.kop.common.response.ApiResponse;
import com.kop.common.response.PageMeta;
import com.kop.finance.dto.TaxInvoiceDto;
import com.kop.finance.issuance.TaxInvoiceSupplierInfo;
import com.kop.finance.security.EmployeePrincipal;
import com.kop.finance.service.TaxInvoiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

/** 06-api-design.md Finance API — 세금계산서 `/api/tax-invoices`. 전부 관리자 전용. */
@RestController
@RequestMapping("/api/tax-invoices")
@RequiredArgsConstructor
public class TaxInvoiceController {

    private final TaxInvoiceService taxInvoiceService;

    @PostMapping
    public ResponseEntity<ApiResponse<TaxInvoiceDto.Response>> register(
        @AuthenticationPrincipal EmployeePrincipal principal, @RequestBody @Valid TaxInvoiceDto.RegisterRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(taxInvoiceService.register(principal, request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Iterable<TaxInvoiceDto.ListItem>>> list(
        @AuthenticationPrincipal EmployeePrincipal principal,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) String clientId,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        // B-3 기본값 — 기간 미지정 시 "이번 달 1일~오늘"(영수증 A-2와 동일 관례).
        LocalDate today = LocalDate.now();
        LocalDate effectiveFrom = from != null ? from : today.withDayOfMonth(1);
        LocalDate effectiveTo = to != null ? to : today;

        TaxInvoiceService.ListResult result = taxInvoiceService.list(principal, effectiveFrom, effectiveTo, clientId, status, PageRequest.of(page - 1, size));
        Page<TaxInvoiceDto.ListItem> items = result.taxInvoices().map(TaxInvoiceDto.ListItem::from);
        return ResponseEntity.ok(ApiResponse.of(items.getContent(), PageMeta.from(items, result.supplyAmount(), result.taxAmount())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TaxInvoiceDto.Response>> getDetail(
        @AuthenticationPrincipal EmployeePrincipal principal, @PathVariable UUID id
    ) {
        return ResponseEntity.ok(ApiResponse.of(taxInvoiceService.getDetail(principal, id)));
    }

    /** 공급자(자사) 정보는 프론트가 이미 갖고 있는 회사 정보를 쿼리 파라미터로 실어 보낸다(TaxInvoiceSupplierInfo 참고). */
    @GetMapping("/{id}/excel")
    public ResponseEntity<byte[]> issueExcel(
        @AuthenticationPrincipal EmployeePrincipal principal, @PathVariable UUID id,
        @RequestParam String supplierBrn, @RequestParam String supplierName
    ) {
        byte[] file = taxInvoiceService.issueExcel(principal, id, new TaxInvoiceSupplierInfo(supplierBrn, supplierName));
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"tax-invoice-" + id + ".xlsx\"")
            .body(file);
    }

    @PostMapping("/{id}/revise")
    public ResponseEntity<ApiResponse<TaxInvoiceDto.Response>> revise(
        @AuthenticationPrincipal EmployeePrincipal principal, @PathVariable UUID id, @RequestBody @Valid TaxInvoiceDto.RegisterRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(taxInvoiceService.revise(principal, id, request)));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<TaxInvoiceDto.Response>> cancel(
        @AuthenticationPrincipal EmployeePrincipal principal, @PathVariable UUID id, @RequestBody @Valid TaxInvoiceDto.CancelRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.of(taxInvoiceService.cancel(principal, id, request)));
    }
}
