package com.kop.finance.service;

import com.kop.common.exception.BusinessException;
import com.kop.common.exception.EntityNotFoundException;
import com.kop.finance.domain.Client;
import com.kop.finance.domain.TaxInvoice;
import com.kop.finance.domain.TaxInvoiceStatus;
import com.kop.finance.dto.TaxInvoiceDto;
import com.kop.finance.issuance.TaxInvoiceIssuanceGateway;
import com.kop.finance.issuance.TaxInvoiceSupplierInfo;
import com.kop.finance.repository.ClientRepository;
import com.kop.finance.repository.TaxInvoiceRepository;
import com.kop.finance.security.EmployeePrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 03-feature-spec.md 모듈 B(세금계산서) B-2~B-4. 전부 관리자 전용. */
@Service
@RequiredArgsConstructor
public class TaxInvoiceService {

    private static final int MAX_ITEMS = 16; // 홈택스 bulk 업로드 양식 제한(B-2)
    private static final Set<String> CREATABLE_STATUSES = Set.of("DRAFT", "COMPLETED");

    private final TaxInvoiceRepository taxInvoiceRepository;
    private final ClientRepository clientRepository;
    private final TaxInvoiceIssuanceGateway issuanceGateway;

    @Transactional
    public TaxInvoiceDto.Response register(EmployeePrincipal principal, TaxInvoiceDto.RegisterRequest request) {
        requireAdmin(principal);
        UUID companyId = UUID.fromString(principal.companyId());

        TaxInvoiceStatus status = parseCreatableStatus(request.status());
        Client client = findClient(companyId, request.clientId());

        TaxInvoice taxInvoice = TaxInvoice.create(companyId, UUID.fromString(principal.employeeId()), client, request.issueDate(), status, request.note());
        taxInvoice.replaceItems(toItemInputs(request.items()));

        // saveAndFlush — @CreationTimestamp(createdAt)·품목 id는 flush 시점에 채워진다(ReceiptService와 같은 이유).
        return TaxInvoiceDto.Response.from(taxInvoiceRepository.saveAndFlush(taxInvoice));
    }

    @Transactional(readOnly = true)
    public ListResult list(
        EmployeePrincipal principal, LocalDate from, LocalDate to, String clientIdParam, String statusParam, Pageable pageable
    ) {
        requireAdmin(principal);
        UUID companyId = UUID.fromString(principal.companyId());
        UUID clientId = parseUuidOrNull(clientIdParam);
        TaxInvoiceStatus status = statusParam == null || statusParam.isBlank() ? null : parseStatus(statusParam);

        Page<TaxInvoice> page = taxInvoiceRepository.search(companyId, from, to, clientId, status, pageable);
        long supplySum = taxInvoiceRepository.sumSupplyAmount(companyId, from, to, clientId, status);
        long taxSum = taxInvoiceRepository.sumTaxAmount(companyId, from, to, clientId, status);
        return new ListResult(page, supplySum, taxSum);
    }

    @Transactional(readOnly = true)
    public TaxInvoiceDto.Response getDetail(EmployeePrincipal principal, UUID id) {
        requireAdmin(principal);
        return TaxInvoiceDto.Response.from(findOwned(principal, id));
    }

    /** B-2 "엑셀 다운로드 시 상태: 엑셀다운로드완료로 변경". */
    @Transactional
    public byte[] issueExcel(EmployeePrincipal principal, UUID id, TaxInvoiceSupplierInfo supplier) {
        requireAdmin(principal);
        TaxInvoice taxInvoice = findOwned(principal, id);
        assertNotCanceled(taxInvoice);

        byte[] file = issuanceGateway.issue(taxInvoice, supplier);
        taxInvoice.markExcelDownloaded();
        return file;
    }

    /** B-4 수정 발행 — 새 건 생성, 원본은 REVISED로 전환(삭제 아님). */
    @Transactional
    public TaxInvoiceDto.Response revise(EmployeePrincipal principal, UUID id, TaxInvoiceDto.RegisterRequest request) {
        requireAdmin(principal);
        TaxInvoice original = findOwned(principal, id);
        assertNotCanceled(original);
        UUID companyId = UUID.fromString(principal.companyId());

        TaxInvoiceStatus status = parseCreatableStatus(request.status());
        Client client = findClient(companyId, request.clientId());

        TaxInvoice revision = TaxInvoice.createRevision(
            companyId, UUID.fromString(principal.employeeId()), client, request.issueDate(), status, request.note(), original.getId()
        );
        revision.replaceItems(toItemInputs(request.items()));
        original.markRevised();

        return TaxInvoiceDto.Response.from(taxInvoiceRepository.saveAndFlush(revision));
    }

    /** B-4 취소 — 사유 필수, 취소 후 집계에서 제외(상태 기반 조건으로 이미 반영됨). */
    @Transactional
    public TaxInvoiceDto.Response cancel(EmployeePrincipal principal, UUID id, TaxInvoiceDto.CancelRequest request) {
        requireAdmin(principal);
        TaxInvoice taxInvoice = findOwned(principal, id);
        assertNotCanceled(taxInvoice);

        taxInvoice.cancel(request.reason());
        return TaxInvoiceDto.Response.from(taxInvoice);
    }

    private Client findClient(UUID companyId, String clientId) {
        UUID id = parseUuidOrNull(clientId);
        if (id == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "거래처를 선택하세요");
        }
        return clientRepository.findByIdAndCompanyId(id, companyId)
            .orElseThrow(() -> new EntityNotFoundException("거래처", id));
    }

    private TaxInvoice findOwned(EmployeePrincipal principal, UUID id) {
        UUID companyId = UUID.fromString(principal.companyId());
        return taxInvoiceRepository.findByIdAndCompanyId(id, companyId)
            .orElseThrow(() -> new EntityNotFoundException("세금계산서", id));
    }

    private void assertNotCanceled(TaxInvoice taxInvoice) {
        if (taxInvoice.isCanceled()) {
            throw new BusinessException(HttpStatus.CONFLICT, "TAX_INVOICE_CANCELED", "이미 취소된 세금계산서입니다");
        }
    }

    private void requireAdmin(EmployeePrincipal principal) {
        if (!principal.isAdmin()) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "이 작업을 수행할 권한이 없습니다");
        }
    }

    private TaxInvoiceStatus parseCreatableStatus(String value) {
        if (!CREATABLE_STATUSES.contains(value)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "작성 시 상태는 DRAFT 또는 COMPLETED만 가능합니다: " + value);
        }
        return TaxInvoiceStatus.valueOf(value);
    }

    private TaxInvoiceStatus parseStatus(String value) {
        try {
            return TaxInvoiceStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "지원하지 않는 상태값입니다: " + value);
        }
    }

    private UUID parseUuidOrNull(String value) {
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }

    private List<TaxInvoice.ItemInput> toItemInputs(List<TaxInvoiceDto.ItemRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "품목은 최소 1개 이상 등록해야 합니다");
        }
        if (items.size() > MAX_ITEMS) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "품목은 최대 " + MAX_ITEMS + "개까지 등록할 수 있습니다");
        }
        return items.stream().map(i -> new TaxInvoice.ItemInput(i.name(), i.spec(), i.quantity(), i.unitPrice())).toList();
    }

    public record ListResult(Page<TaxInvoice> taxInvoices, long supplyAmount, long taxAmount) {}
}
