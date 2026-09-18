package com.kop.finance.openbanking;

import com.fasterxml.jackson.annotation.JsonProperty;

/** GET /v2.0/account/transaction_list/fin_num 응답의 res_list[] 항목 — OpenBankingClient 내부 전용 역직렬화 DTO. */
record OpenBankingTransactionEntry(
    @JsonProperty("tran_date") String tranDate,
    @JsonProperty("tran_time") String tranTime,
    @JsonProperty("inout_type") String inoutType,
    @JsonProperty("tran_amt") Long tranAmt,
    @JsonProperty("print_content") String printContent,
    @JsonProperty("after_balance_amt") Long afterBalanceAmt
) {}
