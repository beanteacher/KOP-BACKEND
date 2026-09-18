package com.kop.finance.openbanking;

import com.kop.common.exception.BusinessException;
import com.kop.finance.gateway.BankTransaction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 금융결제원 오픈뱅킹 OAuth2 클라이언트 — "오픈뱅킹공동업무 이용기관 API 명세서"의 인증
 * URL/토큰발급/사용자 계좌조회/거래내역조회/토큰갱신을 감싼다.
 */
@Component
public class OpenBankingClient {

    private static final String SCOPE = "login inquiry";
    private static final String SUCCESS_CODE = "A0000";
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd
    private static final DateTimeFormatter DATETIME14 = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter TIME6 = DateTimeFormatter.ofPattern("HHmmss");
    private static final String BANK_TRAN_ID_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RestClient restClient;
    private final String baseUrl;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String institutionCode;

    public OpenBankingClient(
        RestClient.Builder restClientBuilder,
        @Value("${openbanking.base-url}") String baseUrl,
        @Value("${openbanking.client-id}") String clientId,
        @Value("${openbanking.client-secret}") String clientSecret,
        @Value("${openbanking.redirect-uri}") String redirectUri,
        @Value("${openbanking.institution-code:}") String institutionCode
    ) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.baseUrl = baseUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.institutionCode = institutionCode;
    }

    /**
     * 사용자를 은행 인증 화면으로 보낼 URL. state는 호출자(OpenBankingStateSigner)가 서명해 넘긴다.
     * .encode()가 꼭 필요하다 — 안 하면 redirect_uri의 "http://..." 같은 값의 콜론/슬래시가
     * percent-encoding 없이 그대로 들어가 오픈뱅킹 서버가 파라미터를 제대로 못 읽는다(실제로
     * 테스트베드에서 O0001/3000103 "필수 파라미터 값이 존재하지 않을 때" 오류로 재현 확인함).
     */
    public String buildAuthorizeUrl(String state) {
        return UriComponentsBuilder.fromUriString(baseUrl)
            .path("/oauth/2.0/authorize")
            .queryParam("response_type", "code")
            .queryParam("client_id", clientId)
            .queryParam("redirect_uri", redirectUri)
            .queryParam("scope", SCOPE)
            .queryParam("state", state)
            .queryParam("auth_type", "0")
            .encode()
            .build()
            .toUriString();
    }

    /** POST /oauth/2.0/token, grant_type=authorization_code — 콜백으로 받은 code를 토큰으로 교환한다. */
    public OpenBankingTokenResponse exchangeToken(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", redirectUri);
        form.add("code", code);

        try {
            return restClient.post()
                .uri("/oauth/2.0/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(OpenBankingTokenResponse.class);
        } catch (RestClientException e) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "OPENBANKING_TOKEN_ERROR", "오픈뱅킹 토큰 발급에 실패했습니다");
        }
    }

    /** GET /v2.0/user/me — 사용자가 인증 과정에서 연결한 계좌 목록. */
    public List<OpenBankingAccount> fetchAccounts(String userSeqNo, String accessToken) {
        OpenBankingAccountListResponse response;
        try {
            response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/v2.0/user/me").queryParam("user_seq_no", userSeqNo).build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(OpenBankingAccountListResponse.class);
        } catch (RestClientException e) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "OPENBANKING_ACCOUNT_FETCH_ERROR", "연결된 계좌 조회에 실패했습니다");
        }

        if (response == null || !SUCCESS_CODE.equals(response.rspCode())) {
            String message = response == null ? "빈 응답" : response.rspMessage();
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "OPENBANKING_ACCOUNT_FETCH_ERROR", "연결된 계좌 조회에 실패했습니다: " + message);
        }
        return response.resList() == null ? List.of() : response.resList();
    }

    /**
     * GET /v2.0/account/transaction_list/fin_num — 입금 내역만 조회한다(inquiry_type=I).
     * 응답에 거래 단위 고유번호가 없다 — bank_tran_id는 "이 조회 요청" 하나를 식별하는 값일 뿐
     * 개별 거래 식별자가 아니다(공식 API 명세서 §2.3.2). 입금자명 전용 필드도 없어서
     * print_content(통장인자내용)를 매칭에 쓴다({@link BankTransaction} 참고).
     */
    public List<BankTransaction> fetchTransactionHistory(String fintechUseNum, String accessToken, LocalDate from, LocalDate to) {
        if (institutionCode == null || institutionCode.isBlank()) {
            throw new IllegalStateException(
                "openbanking.institution-code(OPENBANKING_INSTITUTION_CODE)가 설정되지 않았습니다 — " +
                "developers.openbanking.or.kr에서 발급받은 이용기관코드(10자리)가 필요합니다."
            );
        }

        String bankTranId = generateBankTranId();
        String tranDtime = DATETIME14.format(LocalDateTime.now());

        OpenBankingTransactionListResponse response;
        try {
            response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/v2.0/account/transaction_list/fin_num")
                    .queryParam("bank_tran_id", bankTranId)
                    .queryParam("fintech_use_num", fintechUseNum)
                    .queryParam("inquiry_type", "I")
                    .queryParam("inquiry_base", "D")
                    .queryParam("from_date", from.format(DATE))
                    .queryParam("to_date", to.format(DATE))
                    .queryParam("sort_order", "D")
                    .queryParam("tran_dtime", tranDtime)
                    .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(OpenBankingTransactionListResponse.class);
        } catch (RestClientException e) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "OPENBANKING_TRANSACTION_FETCH_ERROR", "거래내역 조회에 실패했습니다");
        }

        if (response == null || !SUCCESS_CODE.equals(response.rspCode())) {
            String message = response == null ? "빈 응답" : response.rspMessage();
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "OPENBANKING_TRANSACTION_FETCH_ERROR", "거래내역 조회에 실패했습니다: " + message);
        }
        return response.resList() == null ? List.of() : response.resList().stream().map(this::toBankTransaction).toList();
    }

    /**
     * POST /oauth/2.0/token, grant_type=refresh_token — access_token 갱신. 응답에 새
     * refresh_token은 없다(최초 authorization_code 교환 때만 발급) — 기존 refresh_token을
     * 계속 쓴다. 요청 scope는 최초 발급 때와 같은 값이어야 한다("허용된 scope를 넘을 수 없음").
     */
    public OpenBankingTokenResponse refreshToken(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("refresh_token", refreshToken);
        form.add("scope", SCOPE);

        try {
            return restClient.post()
                .uri("/oauth/2.0/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(OpenBankingTokenResponse.class);
        } catch (RestClientException e) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "OPENBANKING_TOKEN_ERROR", "오픈뱅킹 토큰 갱신에 실패했습니다");
        }
    }

    private BankTransaction toBankTransaction(OpenBankingTransactionEntry entry) {
        LocalDateTime dateTime = LocalDateTime.of(
            LocalDate.parse(entry.tranDate(), DATE), java.time.LocalTime.parse(entry.tranTime(), TIME6)
        );
        BankTransaction.TransactionType type = switch (entry.inoutType()) {
            case "입금" -> BankTransaction.TransactionType.DEPOSIT;
            case "출금" -> BankTransaction.TransactionType.WITHDRAWAL;
            default -> BankTransaction.TransactionType.OTHER;
        };
        return new BankTransaction(dateTime, type, entry.tranAmt(), entry.printContent(), entry.afterBalanceAmt());
    }

    /** 3.11. 거래고유번호(참가기관) 생성 안내 — 이용기관코드(10) + "U"(고정) + 이용기관 부여번호(9), 총 20자.
     * 달력상 하루 안에서만 유일하면 된다(명세서 기준) — 매 호출 랜덤 9자로 충분히 만족한다. */
    private String generateBankTranId() {
        StringBuilder suffix = new StringBuilder(9);
        for (int i = 0; i < 9; i++) {
            suffix.append(BANK_TRAN_ID_CHARS.charAt(RANDOM.nextInt(BANK_TRAN_ID_CHARS.length())));
        }
        return institutionCode + "U" + suffix;
    }
}
