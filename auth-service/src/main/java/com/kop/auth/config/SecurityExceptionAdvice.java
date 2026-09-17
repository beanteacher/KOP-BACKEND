package com.kop.auth.config;

import com.kop.common.response.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * @PreAuthorize가 던지는 AccessDeniedException은 컨트롤러 메서드 호출 중(AOP)에 발생해서
 * common의 GlobalExceptionHandler.handleUnexpected(catch-all)가 먼저 잡아버린다 —
 * RestAccessDeniedHandler(필터 레벨)까지 내려가지 않는다. 그래서 이 예외만 여기서 별도로 잡는다.
 * AccessDeniedException은 spring-security 의존성이 필요해 common에는 두지 않았다.
 */
@RestControllerAdvice
public class SecurityExceptionAdvice {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse.of("FORBIDDEN", "이 작업을 수행할 권한이 없습니다"));
    }
}
