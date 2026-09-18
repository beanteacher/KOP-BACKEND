package com.kop.finance.openbanking;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** GET /v2.0/account/transaction_list/fin_num 응답 — OpenBankingClient 내부 전용 역직렬화 DTO. */
record OpenBankingTransactionListResponse(
    @JsonProperty("rsp_code") String rspCode,
    @JsonProperty("rsp_message") String rspMessage,
    @JsonProperty("res_list") List<OpenBankingTransactionEntry> resList
) {}
