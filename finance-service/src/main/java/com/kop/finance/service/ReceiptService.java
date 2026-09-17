package com.kop.finance.service;

import com.kop.common.exception.BusinessException;
import com.kop.common.exception.EntityNotFoundException;
import com.kop.finance.domain.Receipt;
import com.kop.finance.domain.ReceiptCategory;
import com.kop.finance.dto.ReceiptDto;
import com.kop.finance.repository.ReceiptRepository;
import com.kop.finance.security.EmployeePrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/** 03-feature-spec.md 모듈 A(영수증 처리) A-1~A-3. 이미지 업로드(A-1 일부)·내보내기(A-4)는 별도 작업. */
@Service
@RequiredArgsConstructor
public class ReceiptService {

    private static final int FREE_MONTHLY_LIMIT = 20;

    private final ReceiptRepository receiptRepository;

    @Transactional
    public ReceiptDto.Response register(EmployeePrincipal principal, ReceiptDto.RegisterRequest request) {
        UUID companyId = UUID.fromString(principal.companyId());

        if ("FREE".equals(principal.plan())) {
            LocalDate today = LocalDate.now();
            Instant monthStart = today.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
            Instant monthEnd = today.withDayOfMonth(1).plusMonths(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
            long countThisMonth = receiptRepository.countInPeriod(companyId, monthStart, monthEnd);
            if (countThisMonth >= FREE_MONTHLY_LIMIT) {
                throw new BusinessException(HttpStatus.FORBIDDEN, "RECEIPT_PLAN_LIMIT_EXCEEDED", "이번 달 무료 등록 한도(20건)를 초과했습니다.");
            }
        }

        ReceiptCategory category = parseCategory(request.category());
        UUID clientId = parseUuidOrNull(request.clientId());

        Receipt receipt = Receipt.register(
            companyId, UUID.fromString(principal.employeeId()), request.receiptDate(),
            request.vendorName(), clientId, category, request.memo()
        );
        receipt.replaceItems(toItemInputs(request.items()));
        // saveAndFlush — @CreationTimestamp(createdAt)는 INSERT 시점(flush)에 채워진다.
        // save()만 하면 트랜잭션이 아직 안 끝나 flush 전이라 응답에 createdAt이 null로 나간다.
        return ReceiptDto.Response.from(receiptRepository.saveAndFlush(receipt));
    }

    @Transactional(readOnly = true)
    public ListResult list(
        EmployeePrincipal principal, LocalDate from, LocalDate to,
        String clientIdParam, String categoryParam, String createdByParam, String vendorName, Pageable pageable
    ) {
        UUID companyId = UUID.fromString(principal.companyId());
        UUID clientId = parseUuidOrNull(clientIdParam);
        ReceiptCategory category = categoryParam == null ? null : parseCategory(categoryParam);
        UUID createdBy = principal.isAdmin() ? parseUuidOrNull(createdByParam) : UUID.fromString(principal.employeeId());
        String vendorNameFilter = (vendorName == null || vendorName.isBlank()) ? null : vendorName.trim();

        Page<Receipt> page = receiptRepository.search(companyId, from, to, clientId, category, createdBy, vendorNameFilter, pageable);
        long totalAmount = receiptRepository.sumAmount(companyId, from, to, clientId, category, createdBy, vendorNameFilter);
        return new ListResult(page, totalAmount);
    }

    @Transactional(readOnly = true)
    public ReceiptDto.Response getDetail(EmployeePrincipal principal, UUID id) {
        Receipt receipt = findOwned(principal, id);
        assertViewable(principal, receipt);
        return ReceiptDto.Response.from(receipt);
    }

    @Transactional
    public ReceiptDto.Response update(EmployeePrincipal principal, UUID id, ReceiptDto.UpdateRequest request) {
        Receipt receipt = findOwned(principal, id);
        assertEditable(principal, receipt);

        ReceiptCategory category = parseCategory(request.category());
        UUID clientId = parseUuidOrNull(request.clientId());
        receipt.update(request.receiptDate(), request.vendorName(), clientId, category, request.memo());
        receipt.replaceItems(toItemInputs(request.items()));
        // saveAndFlush — 새로 추가된 품목의 id(@GeneratedValue)는 cascade PERSIST가 실제로
        // 나가는 flush 시점에 채워진다. register()의 createdAt과 같은 이유로 flush를 강제한다.
        return ReceiptDto.Response.from(receiptRepository.saveAndFlush(receipt));
    }

    @Transactional
    public void delete(EmployeePrincipal principal, UUID id) {
        Receipt receipt = findOwned(principal, id);
        assertEditable(principal, receipt);
        receipt.softDelete();
    }

    private Receipt findOwned(EmployeePrincipal principal, UUID id) {
        UUID companyId = UUID.fromString(principal.companyId());
        return receiptRepository.findByIdAndCompanyIdAndDeletedAtIsNull(id, companyId)
            .orElseThrow(() -> new EntityNotFoundException("영수증", id));
    }

    /** A-2 "직원은 본인이 등록한 건만 조회 가능" — 상세 조회에도 동일하게 적용한다. */
    private void assertViewable(EmployeePrincipal principal, Receipt receipt) {
        if (principal.isAdmin()) return;
        if (!receipt.isOwnedBy(UUID.fromString(principal.employeeId()))) {
            throw forbidden();
        }
    }

    /** A-3 "직원: 본인 등록 건, 등록 후 24시간 이내만 수정 가능 / 관리자: 전체 건 기간 제한 없이". */
    private void assertEditable(EmployeePrincipal principal, Receipt receipt) {
        if (principal.isAdmin()) return;
        UUID employeeId = UUID.fromString(principal.employeeId());
        if (!receipt.isOwnedBy(employeeId) || !receipt.isWithinStaffEditWindow(Instant.now())) {
            throw forbidden();
        }
    }

    private BusinessException forbidden() {
        return new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "이 작업을 수행할 권한이 없습니다");
    }

    private ReceiptCategory parseCategory(String value) {
        try {
            return ReceiptCategory.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "지원하지 않는 항목 분류입니다: " + value);
        }
    }

    private UUID parseUuidOrNull(String value) {
        return value == null ? null : UUID.fromString(value);
    }

    /** 컨트롤러의 @Valid(@NotEmpty)로도 막히지만, Service 단독 호출(테스트 포함)에도 같은 불변식을 지킨다. */
    private List<Receipt.ItemInput> toItemInputs(List<ReceiptDto.ItemRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "품목은 최소 1개 이상 등록해야 합니다");
        }
        return items.stream().map(i -> new Receipt.ItemInput(i.name(), i.spec(), i.quantity(), i.unitPrice())).toList();
    }

    public record ListResult(Page<Receipt> receipts, long totalAmount) {}
}
