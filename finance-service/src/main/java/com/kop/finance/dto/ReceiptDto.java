package com.kop.finance.dto;

import com.kop.finance.domain.Receipt;
import com.kop.finance.domain.ReceiptItem;
import com.kop.finance.service.PaymentMatchingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;

/**
 * 06-api-design.md Finance API — 영수증. backend-conventions.md DTO 규칙 — 도메인당 파일 하나에
 * inner record. paymentStatus는 클라이언트 입력이 아니다 — 등록 시 항상 PENDING으로 시작하고,
 * 이후 전이는 /mark-paid, /cancel 전용 엔드포인트로만 이뤄진다(상태 전이 규칙 위반 방지).
 *
 * amount는 요청에 없다 — items(품목명·수량·단가) 합계를 서버가 계산해 저장한다(05-database-schema.md).
 */
public class ReceiptDto {

    public record ItemRequest(
        @NotBlank(message = "품목명은 필수입니다") @Size(max = 100, message = "품목명은 100자를 넘을 수 없습니다") String name,
        @Size(max = 100, message = "규격은 100자를 넘을 수 없습니다") String spec,
        @NotNull(message = "수량은 필수입니다") @Positive(message = "수량은 0보다 커야 합니다") Integer quantity,
        @NotNull(message = "단가는 필수입니다") @Positive(message = "단가는 0보다 커야 합니다") Long unitPrice
    ) {}

    public record RegisterRequest(
        @NotNull(message = "발행일은 필수입니다") @PastOrPresent(message = "미래 날짜는 등록할 수 없습니다") LocalDate receiptDate,
        @NotBlank(message = "거래처는 필수입니다") @Size(max = 100, message = "거래처명은 100자를 넘을 수 없습니다") String vendorName,
        String clientId,
        @Size(max = 200, message = "메모는 200자를 넘을 수 없습니다") String memo,
        @NotEmpty(message = "품목은 최소 1개 이상 등록해야 합니다") @Valid List<ItemRequest> items
    ) {}

    public record UpdateRequest(
        @NotNull(message = "발행일은 필수입니다") @PastOrPresent(message = "미래 날짜는 등록할 수 없습니다") LocalDate receiptDate,
        @NotBlank(message = "거래처는 필수입니다") @Size(max = 100, message = "거래처명은 100자를 넘을 수 없습니다") String vendorName,
        String clientId,
        @Size(max = 200, message = "메모는 200자를 넘을 수 없습니다") String memo,
        @NotEmpty(message = "품목은 최소 1개 이상 등록해야 합니다") @Valid List<ItemRequest> items
    ) {}

    /** POST /api/receipts/match-payments 응답 — 이번 실행에서 몇 건 중 몇 건을 자동 매칭했는지. */
    public record MatchPaymentsResponse(int pendingCount, int matchedCount) {
        public static MatchPaymentsResponse from(PaymentMatchingService.MatchResult result) {
            return new MatchPaymentsResponse(result.pendingCount(), result.matchedCount());
        }
    }

    public record ItemResponse(String id, String name, String spec, Integer quantity, Long unitPrice, Long amount) {
        public static ItemResponse from(ReceiptItem item) {
            return new ItemResponse(
                item.getId().toString(), item.getName(), item.getSpec(), item.getQuantity(), item.getUnitPrice(), item.getAmount()
            );
        }
    }

    public record Response(
        String id, LocalDate receiptDate, Long amount, String vendorName,
        String clientId, String paymentStatus, Instant paidAt, String memo, List<ItemResponse> items, String createdBy, Instant createdAt
    ) {
        public static Response from(Receipt r) {
            return new Response(
                r.getId().toString(), r.getReceiptDate(), r.getAmount(), r.getVendorName(),
                r.getClientId() == null ? null : r.getClientId().toString(),
                r.getPaymentStatus().name(), r.getPaidAt(), r.getMemo(),
                r.getItems().stream().map(ItemResponse::from).toList(),
                r.getCreatedBy().toString(), r.getCreatedAt()
            );
        }
    }

    public record ListItem(String id, LocalDate receiptDate, Long amount, String vendorName, String paymentStatus) {
        public static ListItem from(Receipt r) {
            return new ListItem(r.getId().toString(), r.getReceiptDate(), r.getAmount(), r.getVendorName(), r.getPaymentStatus().name());
        }
    }
}
