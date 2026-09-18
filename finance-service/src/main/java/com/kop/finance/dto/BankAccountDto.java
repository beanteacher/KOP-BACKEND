package com.kop.finance.dto;

import com.kop.finance.domain.BankAccount;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

/** 06-api-design.md Finance API — 오픈뱅킹 계좌 연결 `/api/bank-accounts`. */
public class BankAccountDto {

    public record AuthorizeUrlResponse(String url) {}

    public record RegisterRequest(
        @NotBlank(message = "인증 코드가 필요합니다") String code,
        @NotBlank(message = "state 값이 필요합니다") String state
    ) {}

    /** connected=false면 나머지 필드는 전부 null — "연결 안 됨"도 정상 응답(200)으로 다룬다. */
    public record Response(boolean connected, String bankName, String accountNumberMasked, Instant connectedAt) {
        public static Response connected(BankAccount account) {
            return new Response(true, account.getBankName(), account.getAccountNumberMasked(), account.getCreatedAt());
        }

        public static Response notConnected() {
            return new Response(false, null, null, null);
        }
    }
}
