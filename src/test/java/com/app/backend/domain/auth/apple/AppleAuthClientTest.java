package com.app.backend.domain.auth.apple;

import com.app.backend.global.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AppleAuthClientTest {

    private MockRestServiceServer server;
    private AppleAuthClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        AppleClientSecretGenerator generator = mock(AppleClientSecretGenerator.class);
        when(generator.generate()).thenReturn("dummy.client.secret");
        when(generator.getClientId()).thenReturn("com.ddara.team3");
        client = new AppleAuthClient(builder, generator);
    }

    @Test
    void exchangeCode_성공시_refresh_token을_반환한다() {
        server.expect(requestTo("https://appleid.apple.com/auth/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"refresh_token\":\"RT-123\",\"access_token\":\"AT-1\"}",
                        MediaType.APPLICATION_JSON));

        String rt = client.exchangeCode("AUTH-CODE");

        assertThat(rt).isEqualTo("RT-123");
        server.verify();
    }

    @Test
    void exchangeCode_실패시_CustomException() {
        server.expect(requestTo("https://appleid.apple.com/auth/token"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.exchangeCode("BAD"))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void revoke_성공시_예외없음() {
        server.expect(requestTo("https://appleid.apple.com/auth/revoke"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());

        client.revoke("RT-123");

        server.verify();
    }

    @Test
    void revoke_실패시_CustomException() {
        server.expect(requestTo("https://appleid.apple.com/auth/revoke"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.revoke("RT-123"))
                .isInstanceOf(CustomException.class);
    }
}
