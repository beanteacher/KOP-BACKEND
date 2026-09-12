package com.kitchensys.common.response;

/** 06-api-design.md 에러 포맷 — {"error": {"code": ..., "message": ...}}. */
public record ErrorResponse(ErrorBody error) {

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(new ErrorBody(code, message));
    }

    public record ErrorBody(String code, String message) {}
}
