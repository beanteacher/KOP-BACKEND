package com.kop.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.data.domain.Page;

/**
 * 06-api-design.md 목록 API 공통 페이지네이션 메타.
 * API의 page는 1-based, Spring Data {@link Page}는 0-based라 변환해서 내려준다.
 * totalAmount는 영수증처럼 "필터 합계"를 같이 내려줘야 하는 목록에서만 채운다(06-api-design.md
 * GET /api/receipts). taxAmount는 세금계산서 이력 조회(B-3)처럼 합계가 두 개(공급가액·세액)
 * 필요한 목록에서만 추가로 채운다. 그런 개념이 없는 목록은 null이라 응답 JSON에서 아예 빠진다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PageMeta(int page, int size, long totalElements, int totalPages, Long totalAmount, Long taxAmount) {

    public static PageMeta from(Page<?> page) {
        return new PageMeta(page.getNumber() + 1, page.getSize(), page.getTotalElements(), page.getTotalPages(), null, null);
    }

    public static PageMeta from(Page<?> page, long totalAmount) {
        return new PageMeta(page.getNumber() + 1, page.getSize(), page.getTotalElements(), page.getTotalPages(), totalAmount, null);
    }

    /** 세금계산서 이력 조회(B-3) 전용 — totalAmount는 공급가액 합계, taxAmount는 세액 합계. */
    public static PageMeta from(Page<?> page, long supplyAmount, long taxAmount) {
        return new PageMeta(page.getNumber() + 1, page.getSize(), page.getTotalElements(), page.getTotalPages(), supplyAmount, taxAmount);
    }
}
