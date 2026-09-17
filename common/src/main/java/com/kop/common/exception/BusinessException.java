package com.kop.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** 06-api-design.md 공통 에러 코드(VALIDATION_ERROR, CONFLICT 등)를 실어 던지는 기본 예외. */
@Getter
public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public BusinessException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
