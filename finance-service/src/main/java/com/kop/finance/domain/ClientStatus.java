package com.kop.finance.domain;

/** 05-database-schema.md finance.clients.status — 세금계산서 이력이 있으면 삭제 대신 비활성화(B-1). */
public enum ClientStatus {
    ACTIVE,
    INACTIVE
}
