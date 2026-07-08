package com.app.backend.domain.auth.apple;

import com.app.backend.global.exception.CustomException;
import com.app.backend.global.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * 애플 OAuth REST API 호출: authorizationCode↔refresh_token 교환, refresh_token 해제(revoke).
 * 애플 서버 응답이 실패면 CustomException으로 변환한다(revoke 실패는 호출부에서 삼켜 로그만 남긴다).
 */
@Component
public class AppleAuthClient {

    private static final Logger log = LoggerFactory.getLogger(AppleAuthClient.class);
    private static final String BASE_URL = "https://appleid.apple.com";

    private final RestClient restClient;
    private final AppleClientSecretGenerator secretGenerator;

    public AppleAuthClient(RestClient.Builder builder, AppleClientSecretGenerator secretGenerator) {
        this.restClient = builder.baseUrl(BASE_URL).build();
        this.secretGenerator = secretGenerator;
    }

    /** authorizationCode를 refresh_token으로 교환한다. */
    public String exchangeCode(String authorizationCode) {
        MultiValueMap<String, String> form = baseForm();
        form.add("grant_type", "authorization_code");
        form.add("code", authorizationCode);
        try {
            AppleTokenResponse res = restClient.post()
                    .uri("/auth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(AppleTokenResponse.class);
            if (res == null || res.refreshToken() == null) {
                throw new CustomException(ErrorCode.APPLE_TOKEN_EXCHANGE_FAILED);
            }
            return res.refreshToken();
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.warn("애플 토큰 교환 실패", e);
            throw new CustomException(ErrorCode.APPLE_TOKEN_EXCHANGE_FAILED);
        }
    }

    /** refresh_token으로 애플 연동을 해제한다. */
    public void revoke(String refreshToken) {
        MultiValueMap<String, String> form = baseForm();
        form.add("token", refreshToken);
        form.add("token_type_hint", "refresh_token");
        try {
            restClient.post()
                    .uri("/auth/revoke")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("애플 연동 해제(revoke) 실패", e);
            throw new CustomException(ErrorCode.APPLE_REVOKE_FAILED);
        }
    }

    private MultiValueMap<String, String> baseForm() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", secretGenerator.getClientId());
        form.add("client_secret", secretGenerator.generate());
        return form;
    }
}
