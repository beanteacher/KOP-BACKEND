package com.kitchensys.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 06-api-design.md 공통 응답 포맷 — {"data": ...} 단건, {"data": [...], "meta": {...}} 목록.
 * 성공 응답 전용. 에러는 {@link ErrorResponse}로 별도 처리한다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(T data, PageMeta meta) {

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data, null);
    }

    public static <T> ApiResponse<T> of(T data, PageMeta meta) {
        return new ApiResponse<>(data, meta);
    }
}
