package com.kop.finance.openbanking;

import com.fasterxml.jackson.annotation.JsonProperty;

/** GET /v2.0/user/me 응답의 res_list[] 항목 — 계좌 연결에 필요한 필드만 옮겨 담는다. */
public record OpenBankingAccount(
    @JsonProperty("fintech_use_num") String fintechUseNum,
    @JsonProperty("bank_name") String bankName,
    @JsonProperty("account_num_masked") String accountNumMasked,
    @JsonProperty("account_holder_name") String accountHolderName
) {}
