package com.kop.finance.dto;

import com.kop.finance.domain.TaxInvoice;
import com.kop.finance.domain.TaxInvoiceItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 06-api-design.md Finance API — 세금계산서. status는 enum이 아니라 String으로 받는다(ReceiptDto의
 * category 처리와 같은 이유 — Jackson이 잘못된 값을 곧장 500으로 떨어뜨리는 걸 막고, Service에서
 * 직접 검증해 400으로 응답한다).
 */
public class TaxInvoiceDto {

    public record ItemRequest(
        @NotBlank(message = "품목명은 필수입니다") @Size(max = 100, message = "품목명은 100자를 넘을 수 없습니다") String name,
        @Size(max = 100, message = "규격은 100자를 넘을 수 없습니다") String spec,
        @NotNull(message = "수량은 필수입니다") @Positive(message = "수량은 0보다 커야 합니다") Integer quantity,
        @NotNull(message = "단가는 필수입니다") @PositiveOrZero(message = "단가는 0 이상이어야 합니다") Long unitPrice
    ) {}

    /** status는 "DRAFT" 또는 "COMPLETED"만 허용 — 그 외 상태는 전이 전용 엔드포인트(취소/수정발행/엑셀)로만 도달한다. */
    public record RegisterRequest(
        @NotBlank(message = "거래처를 선택하세요") String clientId,
        @NotNull(message = "작성일자는 필수입니다") LocalDate issueDate,
        @NotBlank(message = "상태는 필수입니다") String status,
        @NotEmpty(message = "품목은 최소 1개 이상 등록해야 합니다") @Valid List<ItemRequest> items,
        @Size(max = 200, message = "비고는 200자를 넘을 수 없습니다") String note
    ) {}

    public record CancelRequest(@NotBlank(message = "취소 사유는 필수입니다") @Size(max = 100, message = "취소 사유는 100자를 넘을 수 없습니다") String reason) {}

    public record ItemResponse(String name, String spec, Integer quantity, Long unitPrice, Long supplyAmount, Long taxAmount) {
        public static ItemResponse from(TaxInvoiceItem item) {
            return new ItemResponse(item.getName(), item.getSpec(), item.getQuantity(), item.getUnitPrice(), item.getSupplyAmount(), item.getTaxAmount());
        }
    }

    public record Response(
        String id, String clientId, String clientName, String clientBusinessRegistrationNumber,
        LocalDate issueDate, String status, String issueMethod, List<ItemResponse> items,
        Long supplyAmount, Long taxAmount, Long totalAmount, String note,
        String revisedFromId, String cancelReason, Instant canceledAt, Instant excelDownloadedAt, Instant createdAt
    ) {
        public static Response from(TaxInvoice ti) {
            return new Response(
                ti.getId().toString(), ti.getClient().getId().toString(), ti.getClient().getName(), ti.getClient().getBusinessRegistrationNumber(),
                ti.getIssueDate(), ti.getStatus().name(), ti.getIssueMethod().name(),
                ti.getItems().stream().map(ItemResponse::from).toList(),
                ti.getSupplyAmount(), ti.getTaxAmount(), ti.getTotalAmount(), ti.getNote(),
                ti.getRevisedFromId() == null ? null : ti.getRevisedFromId().toString(),
                ti.getCancelReason(), ti.getCanceledAt(), ti.getExcelDownloadedAt(), ti.getCreatedAt()
            );
        }
    }

    public record ListItem(
        String id, LocalDate issueDate, String clientName, Long supplyAmount, Long taxAmount, Long totalAmount, String status
    ) {
        public static ListItem from(TaxInvoice ti) {
            return new ListItem(
                ti.getId().toString(), ti.getIssueDate(), ti.getClient().getName(),
                ti.getSupplyAmount(), ti.getTaxAmount(), ti.getTotalAmount(), ti.getStatus().name()
            );
        }
    }
}
