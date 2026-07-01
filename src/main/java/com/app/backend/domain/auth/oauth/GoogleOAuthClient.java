package com.app.backend.domain.auth.oauth;

import com.app.backend.domain.user.entity.AuthProvider;
import com.app.backend.global.exception.CustomException;
import com.app.backend.global.exception.ErrorCode;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class GoogleOAuthClient implements OAuthClient {

    private static final String USER_INFO_URI = "https://www.googleapis.com/oauth2/v3/userinfo";

    private final RestClient restClient;

    public GoogleOAuthClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Override
    public AuthProvider getProvider() {
        return AuthProvider.GOOGLE;
    }

    @Override
    public OAuthUserInfo getUserInfo(String accessToken) {
        GoogleUserResponse response;
        try {
            response = restClient.get()
                    .uri(USER_INFO_URI)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(GoogleUserResponse.class);
        } catch (RestClientException e) {
            throw new CustomException(ErrorCode.INVALID_OAUTH_TOKEN);
        }

        if (response == null || response.sub() == null) {
            throw new CustomException(ErrorCode.INVALID_OAUTH_TOKEN);
        }

        return new OAuthUserInfo(response.sub(), response.name());
    }

    private record GoogleUserResponse(String sub, String name) {
    }
}