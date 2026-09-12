package com.kitchensys.common.response;

import org.springframework.data.domain.Page;

/**
 * 06-api-design.md 목록 API 공통 페이지네이션 메타.
 * API의 page는 1-based, Spring Data {@link Page}는 0-based라 변환해서 내려준다.
 */
public record PageMeta(int page, int size, long totalElements, int totalPages) {

    public static PageMeta from(Page<?> page) {
        return new PageMeta(page.getNumber() + 1, page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
