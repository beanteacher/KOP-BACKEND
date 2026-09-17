package com.kop.common.exception;

import org.springframework.http.HttpStatus;

/** backend-conventions.md — 엔티티마다 별도 예외를 만들지 않고 이 하나를 공용으로 쓴다. */
public class EntityNotFoundException extends BusinessException {

    public EntityNotFoundException(String entityName, Object id) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", entityName + "을(를) 찾을 수 없습니다: " + id);
    }
}
