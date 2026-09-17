package com.kop.common.event;

import java.time.Instant;
import java.util.UUID;

/** 04-system-architecture.md Kafka 이벤트 공통 메시지 구조. */
public record DomainEvent<T>(String eventId, String eventType, String companyId, Instant timestamp, T payload) {

    public static <T> DomainEvent<T> of(String eventType, String companyId, T payload) {
        return new DomainEvent<>(UUID.randomUUID().toString(), eventType, companyId, Instant.now(), payload);
    }
}
