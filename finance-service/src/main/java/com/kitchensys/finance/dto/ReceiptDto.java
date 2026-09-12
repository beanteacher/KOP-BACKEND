package com.kitchensys.finance.dto;

import com.kitchensys.finance.domain.Receipt;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.Instant;

/**
 * 06-api-design.md Finance API — 영수증. backend-conventions.md DTO 규칙 — 도메인당 파일 하나에
 * inner record. category는 enum 타입 대신 String으로 받는다 — @RequestBody가 잘못된 값을 만나면
 * Jackson이 곧장 HttpMessageNotReadableException을 던지는데, GlobalExceptionHandler는 이걸
 * VALIDATION_ERROR로 개별 처리하지 않아 catch-all(500)로 떨어진다(auth-service refresh 500
 * 버그와 같은 종류). Service에서 유효성 검사 후 enum으로 변환해 BusinessException(400)으로 던진다.
 */
public class ReceiptDto {

    public record RegisterRequest(
        @NotNull(message = "발행일은 필수입니다") @PastOrPresent(message = "미래 날짜는 등록할 수 없습니다") LocalDate receiptDate,
        @NotNull(message = "금액은 필수입니다") @Positive(message = "금액은 0보다 커야 합니다") Long amount,
        @NotBlank(message = "거래처는 필수입니다") @Size(max = 100, message = "거래처명은 100자를 넘을 수 없습니다") String vendorName,
        String clientId,
        @NotBlank(message = "항목 분류는 필수입니다") String category,
        @Size(max = 200, message = "메모는 200자를 넘을 수 없습니다") String memo
    ) {}

    public record UpdateRequest(
        @NotNull(message = "발행일은 필수입니다") @PastOrPresent(message = "미래 날짜는 등록할 수 없습니다") LocalDate receiptDate,
        @NotNull(message = "금액은 필수입니다") @Positive(message = "금액은 0보다 커야 합니다") Long amount,
        @NotBlank(message = "거래처는 필수입니다") @Size(max = 100, message = "거래처명은 100자를 넘을 수 없습니다") String vendorName,
        String clientId,
        @NotBlank(message = "항목 분류는 필수입니다") String category,
        @Size(max = 200, message = "메모는 200자를 넘을 수 없습니다") String memo
    ) {}

    public record Response(
        String id, LocalDate receiptDate, Long amount, String vendorName,
        String clientId, String category, String memo, String createdBy, Instant createdAt
    ) {
        public static Response from(Receipt r) {
            return new Response(
                r.getId().toString(), r.getReceiptDate(), r.getAmount(), r.getVendorName(),
                r.getClientId() == null ? null : r.getClientId().toString(),
                r.getCategory().name(), r.getMemo(), r.getCreatedBy().toString(), r.getCreatedAt()
            );
        }
    }

    public record ListItem(String id, LocalDate receiptDate, Long amount, String vendorName, String category) {
        public static ListItem from(Receipt r) {
            return new ListItem(r.getId().toString(), r.getReceiptDate(), r.getAmount(), r.getVendorName(), r.getCategory().name());
        }
    }
}
