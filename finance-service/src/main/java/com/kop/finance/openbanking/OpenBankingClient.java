package com.kop.finance.openbanking;

import com.kop.common.exception.BusinessException;
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

import java.util.List;

/**
 * 금융결제원 오픈뱅킹 OAuth2 클라이언트 — "오픈뱅킹공동업무 이용기관 API 명세서"의 인증
 * URL/토큰발급/사용자 계좌조회 3개 API만 감싼다. 거래내역조회(v2.0/account/transaction_list/fin_num)는
 * 여기 없다 — 계좌 연결(BankAccountService)과 거래내역 조회(BankTransactionGateway 실제
 * 구현체)는 별도 작업으로 분리했다(지금은 MockBankTransactionGateway만 활성).
 */
@Component
public class OpenBankingClient {

    private static final String SCOPE = "login inquiry";
    private static final String SUCCESS_CODE = "A0000";

    private final RestClient restClient;
    private final String baseUrl;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    public OpenBankingClient(
        RestClient.Builder restClientBuilder,
        @Value("${openbanking.base-url}") String baseUrl,
        @Value("${openbanking.client-id}") String clientId,
        @Value("${openbanking.client-secret}") String clientSecret,
        @Value("${openbanking.redirect-uri}") String redirectUri
    ) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.baseUrl = baseUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
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
}
