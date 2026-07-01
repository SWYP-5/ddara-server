package com.app.backend.domain.auth.oauth;

import com.app.backend.domain.user.entity.AuthProvider;
import com.app.backend.global.exception.CustomException;
import com.app.backend.global.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class KakaoOAuthClient implements OAuthClient {

    private static final String USER_INFO_URI = "https://kapi.kakao.com/v2/user/me";

    private final RestClient restClient;

    public KakaoOAuthClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Override
    public AuthProvider getProvider() {
        return AuthProvider.KAKAO;
    }

    @Override
    public OAuthUserInfo getUserInfo(String accessToken) {
        KakaoUserResponse response;
        try {
            response = restClient.get()
                    .uri(USER_INFO_URI)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(KakaoUserResponse.class);
        } catch (RestClientException e) {
            throw new CustomException(ErrorCode.INVALID_OAUTH_TOKEN);
        }

        if (response == null || response.id() == null) {
            throw new CustomException(ErrorCode.INVALID_OAUTH_TOKEN);
        }

        String nickname = response.kakaoAccount() != null && response.kakaoAccount().profile() != null
                ? response.kakaoAccount().profile().nickname()
                : null;
        return new OAuthUserInfo(String.valueOf(response.id()), nickname);
    }

    private record KakaoUserResponse(Long id, @JsonProperty("kakao_account") KakaoAccount kakaoAccount) {
        private record KakaoAccount(Profile profile) {
        }

        private record Profile(String nickname) {
        }
    }
}