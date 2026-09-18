package com.kop.finance.openbanking;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** GET /v2.0/user/me 응답 — OpenBankingClient 내부에서만 쓰는 역직렬화 전용 DTO. */
record OpenBankingAccountListResponse(
    @JsonProperty("rsp_code") String rspCode,
    @JsonProperty("rsp_message") String rspMessage,
    @JsonProperty("res_cnt") Integer resCnt,
    @JsonProperty("res_list") List<OpenBankingAccount> resList
) {}
