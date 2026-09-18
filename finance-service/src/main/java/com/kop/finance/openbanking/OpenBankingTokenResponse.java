package com.kop.finance.openbanking;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 오픈뱅킹공동업무 이용기관 API 명세서 §POST /oauth/2.0/token 응답. */
public record OpenBankingTokenResponse(
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("refresh_token") String refreshToken,
    @JsonProperty("expires_in") Long expiresIn,
    @JsonProperty("user_seq_no") String userSeqNo,
    String scope
) {}
