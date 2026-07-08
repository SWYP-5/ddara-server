# 애플 로그인 연동 해제 (탈퇴 시 revoke) 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 애플로 가입한 사용자가 탈퇴하면 백엔드가 애플 `/auth/revoke`를 호출해 Sign in with Apple 연동을 해제한다(App Store 심사 규정 5.1.1(v)).

**Architecture:** 로그인/가입 때 iOS가 보낸 애플 `authorizationCode`를 백엔드가 애플과 교환해 `refresh_token`을 `User`에 저장하고, 탈퇴 시 그 토큰으로 revoke를 호출한다. 애플 호출용 `client_secret`은 `.p8` 개인키로 서명한 ES256 JWT다.

**Tech Stack:** Spring Boot 3(가상스레드), JPA/MySQL, `io.jsonwebtoken:jjwt 0.12.5`(ES256 서명), Spring `RestClient`, JUnit5 + `MockRestServiceServer`.

**Base branch:** `feat/apple-revoke-on-withdrawal` (origin/develop 기반). 스펙: `docs/superpowers/specs/2026-07-08-apple-revoke-on-withdrawal-design.md`.

**애플 인증정보:** Team ID `3PVV8DQPL6` / Key ID `HBT5YX26FZ` / client_id(Bundle ID) `com.ddara.team3` / 개인키 `AuthKey_HBT5YX26FZ.p8`(현재 `~/Downloads`에 있음).

---

## 파일 구조

- Create `src/main/java/com/app/backend/domain/auth/apple/AppleClientSecretGenerator.java` — `.p8` 로드 + ES256 client_secret JWT 생성
- Create `src/main/java/com/app/backend/domain/auth/apple/AppleAuthClient.java` — 애플 `/auth/token`·`/auth/revoke` 호출
- Create `src/main/java/com/app/backend/domain/auth/apple/AppleTokenResponse.java` — 토큰 응답 매핑 record
- Modify `src/main/java/com/app/backend/global/exception/ErrorCode.java` — 애플 관련 에러코드 추가
- Modify `src/main/java/com/app/backend/domain/user/entity/User.java` — `appleRefreshToken` 필드/메서드, `withdraw()`에서 정리
- Modify `src/main/java/com/app/backend/domain/auth/dto/AppleLoginRequest.java` — `appleAuthorizationCode` 추가
- Modify `src/main/java/com/app/backend/domain/auth/dto/SignupRequest.java` — `appleAuthorizationCode` 추가
- Modify `src/main/java/com/app/backend/domain/auth/service/AuthService.java` — 로그인/가입 시 code 교환·저장
- Modify `src/main/java/com/app/backend/domain/auth/controller/AuthController.java` — code 전달
- Modify `src/main/java/com/app/backend/domain/user/service/UserService.java` — 탈퇴 시 revoke
- Modify `src/main/resources/application.yml`, `application-dev.yml` — `apple.*` 설정
- Modify `.gitignore` — `.p8` 커밋 금지
- Add(비커밋) `src/main/resources/apple/AuthKey_HBT5YX26FZ.p8`
- Tests: `src/test/java/com/app/backend/domain/auth/apple/AppleClientSecretGeneratorTest.java`, `AppleAuthClientTest.java`, `src/test/java/com/app/backend/domain/user/entity/UserWithdrawTest.java`, 서비스 테스트

---

## Task 1: 에러코드 추가

**Files:**
- Modify: `src/main/java/com/app/backend/global/exception/ErrorCode.java`

- [ ] **Step 1: 애플 에러코드 추가**

`UNSUPPORTED_OAUTH_PROVIDER(...)` 줄 바로 아래에 추가:

```java
    APPLE_KEY_UNAVAILABLE(HttpStatus.INTERNAL_SERVER_ERROR, "애플 인증 키를 사용할 수 없습니다."),
    APPLE_TOKEN_EXCHANGE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "애플 토큰 교환에 실패했습니다."),
    APPLE_REVOKE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "애플 연동 해제에 실패했습니다."),
```

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew compileJava -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/app/backend/global/exception/ErrorCode.java
git commit -m "feat: 애플 로그인 연동 해제용 에러코드 추가"
```

---

## Task 2: AppleClientSecretGenerator (client_secret JWT 생성)

`.p8`(PKCS#8 PEM)을 EC 개인키로 파싱하고, 애플 규격의 ES256 JWT를 만든다. 파싱/생성 로직을 파일 로드와 분리해 단위 테스트가 가능하게 한다.

**Files:**
- Create: `src/main/java/com/app/backend/domain/auth/apple/AppleClientSecretGenerator.java`
- Test: `src/test/java/com/app/backend/domain/auth/apple/AppleClientSecretGeneratorTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package com.app.backend.domain.auth.apple;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class AppleClientSecretGeneratorTest {

    // P-256 EC 키쌍 생성 (테스트용 애플 .p8 흉내)
    private KeyPair p256() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"));
        return g.generateKeyPair();
    }

    @Test
    void parsePrivateKey_PKCS8_PEM을_EC키로_파싱한다() throws Exception {
        KeyPair kp = p256();
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(kp.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";

        var key = AppleClientSecretGenerator.parsePrivateKey(pem);

        assertThat(key).isInstanceOf(ECPrivateKey.class);
    }

    @Test
    void buildClientSecret_애플_규격_클레임과_ES256_서명을_생성한다() throws Exception {
        KeyPair kp = p256();
        Instant now = Instant.parse("2026-07-08T00:00:00Z");

        String jwt = AppleClientSecretGenerator.buildClientSecret(
                (ECPrivateKey) kp.getPrivate(), "3PVV8DQPL6", "HBT5YX26FZ", "com.ddara.team3", now);

        Jws<Claims> parsed = Jwts.parser().verifyWith(kp.getPublic()).build().parseSignedClaims(jwt);
        assertThat(parsed.getHeader().getKeyId()).isEqualTo("HBT5YX26FZ");
        assertThat(parsed.getHeader().getAlgorithm()).isEqualTo("ES256");
        Claims c = parsed.getPayload();
        assertThat(c.getIssuer()).isEqualTo("3PVV8DQPL6");
        assertThat(c.getSubject()).isEqualTo("com.ddara.team3");
        assertThat(c.getAudience()).contains("https://appleid.apple.com");
        assertThat(c.getExpiration()).isAfter(c.getIssuedAt());
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests "*AppleClientSecretGeneratorTest" -q`
Expected: FAIL — `AppleClientSecretGenerator` 심볼 없음(컴파일 에러)

- [ ] **Step 3: 최소 구현**

```java
package com.app.backend.domain.auth.apple;

import com.app.backend.global.exception.CustomException;
import com.app.backend.global.exception.ErrorCode;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * 애플 REST API 호출용 client_secret(JWT)을 만든다.
 * <p>.p8(PKCS#8 PEM) 개인키로 ES256 서명한다. 키는 최초 사용 시 클래스패스에서 읽어 캐시한다.
 * 키가 없는 환경(CI 등)에서는 빈 생성은 정상이며, 실제 생성 호출 시에만 예외가 난다.
 */
@Component
public class AppleClientSecretGenerator {

    private static final String APPLE_AUDIENCE = "https://appleid.apple.com";
    // client_secret 유효기간(초). 애플 최대 6개월, 짧게 5분만 준다(요청 시마다 새로 생성).
    private static final long EXPIRATION_SECONDS = 300;

    private final String teamId;
    private final String keyId;
    private final String clientId;
    private final String privateKeyPath;

    private volatile ECPrivateKey cachedKey;

    public AppleClientSecretGenerator(
            @Value("${apple.team-id}") String teamId,
            @Value("${apple.key-id}") String keyId,
            @Value("${apple.client-id}") String clientId,
            @Value("${apple.private-key-path}") String privateKeyPath) {
        this.teamId = teamId;
        this.keyId = keyId;
        this.clientId = clientId;
        this.privateKeyPath = privateKeyPath;
    }

    public String getClientId() {
        return clientId;
    }

    /** 지금 시각 기준 client_secret 생성. 키가 없으면 CustomException(APPLE_KEY_UNAVAILABLE). */
    public String generate() {
        return buildClientSecret(loadKey(), teamId, keyId, clientId, Instant.now());
    }

    private ECPrivateKey loadKey() {
        ECPrivateKey key = cachedKey;
        if (key != null) {
            return key;
        }
        ClassPathResource resource = new ClassPathResource(privateKeyPath);
        if (!resource.exists()) {
            throw new CustomException(ErrorCode.APPLE_KEY_UNAVAILABLE);
        }
        try (InputStream in = resource.getInputStream()) {
            String pem = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            key = (ECPrivateKey) parsePrivateKey(pem);
            cachedKey = key;
            return key;
        } catch (Exception e) {
            throw new CustomException(ErrorCode.APPLE_KEY_UNAVAILABLE);
        }
    }

    /** PKCS#8 PEM 문자열을 EC 개인키로 파싱한다. */
    public static PrivateKey parsePrivateKey(String pem) {
        try {
            String base64 = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new CustomException(ErrorCode.APPLE_KEY_UNAVAILABLE);
        }
    }

    /** 애플 규격 client_secret JWT 생성(순수 함수, 테스트 대상). */
    public static String buildClientSecret(ECPrivateKey key, String teamId, String keyId,
                                           String clientId, Instant now) {
        Date iat = Date.from(now);
        Date exp = Date.from(now.plusSeconds(EXPIRATION_SECONDS));
        return Jwts.builder()
                .header().keyId(keyId).and()
                .issuer(teamId)
                .issuedAt(iat)
                .expiration(exp)
                .audience().add(APPLE_AUDIENCE).and()
                .subject(clientId)
                .signWith(key, Jwts.SIG.ES256)
                .compact();
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "*AppleClientSecretGeneratorTest" -q`
Expected: PASS (2 tests)

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/app/backend/domain/auth/apple/AppleClientSecretGenerator.java src/test/java/com/app/backend/domain/auth/apple/AppleClientSecretGeneratorTest.java
git commit -m "feat: 애플 client_secret(ES256 JWT) 생성기 추가"
```

---

## Task 3: AppleAuthClient (토큰 교환 + revoke)

**Files:**
- Create: `src/main/java/com/app/backend/domain/auth/apple/AppleTokenResponse.java`
- Create: `src/main/java/com/app/backend/domain/auth/apple/AppleAuthClient.java`
- Test: `src/test/java/com/app/backend/domain/auth/apple/AppleAuthClientTest.java`

- [ ] **Step 1: 응답 record 작성**

```java
package com.app.backend.domain.auth.apple;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 애플 /auth/token 응답 중 필요한 필드만 매핑. */
public record AppleTokenResponse(
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("access_token") String accessToken
) {
}
```

- [ ] **Step 2: 실패하는 테스트 작성**

```java
package com.app.backend.domain.auth.apple;

import com.app.backend.global.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.springframework.http.HttpMethod;

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
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `./gradlew test --tests "*AppleAuthClientTest" -q`
Expected: FAIL — `AppleAuthClient` 심볼 없음

- [ ] **Step 4: 구현**

```java
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
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew test --tests "*AppleAuthClientTest" -q`
Expected: PASS (4 tests)

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/app/backend/domain/auth/apple/
git add src/test/java/com/app/backend/domain/auth/apple/AppleAuthClientTest.java
git commit -m "feat: 애플 토큰 교환·연동 해제(revoke) API 클라이언트 추가"
```

---

## Task 4: User 엔티티에 apple_refresh_token 추가

**Files:**
- Modify: `src/main/java/com/app/backend/domain/user/entity/User.java`
- Test: `src/test/java/com/app/backend/domain/user/entity/UserWithdrawTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package com.app.backend.domain.user.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UserWithdrawTest {

    private User appleUser() {
        return User.builder()
                .provider(AuthProvider.APPLE)
                .providerId("apple-uid-1")
                .name("최예진")
                .build();
    }

    @Test
    void updateAppleRefreshToken_저장하고_clear로_지운다() {
        User user = appleUser();
        user.updateAppleRefreshToken("RT-1");
        assertThat(user.getAppleRefreshToken()).isEqualTo("RT-1");

        user.clearAppleRefreshToken();
        assertThat(user.getAppleRefreshToken()).isNull();
    }

    @Test
    void withdraw시_appleRefreshToken도_비운다() {
        User user = appleUser();
        user.updateAppleRefreshToken("RT-1");

        user.withdraw(LocalDateTime.now());

        assertThat(user.getAppleRefreshToken()).isNull();
        assertThat(user.isWithdrawn()).isTrue();
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests "*UserWithdrawTest" -q`
Expected: FAIL — `updateAppleRefreshToken`/`getAppleRefreshToken` 없음

- [ ] **Step 3: User 엔티티 수정**

`fcmToken` 필드 아래에 필드 추가:

```java
    // 애플 로그인 refresh_token — 탈퇴 시 애플 연동 해제(revoke)에 사용. 애플 유저만 값 존재. (U-05)
    // 보안상 로그·응답 DTO로 노출 금지.
    @Column(name = "apple_refresh_token", length = 512)
    private String appleRefreshToken;
```

`clearFcmToken()` 메서드 아래에 메서드 추가:

```java
    /** 애플 refresh_token 저장(덮어쓰기) — 애플 로그인 시. */
    public void updateAppleRefreshToken(String appleRefreshToken) {
        this.appleRefreshToken = appleRefreshToken;
    }

    /** 애플 refresh_token 제거. */
    public void clearAppleRefreshToken() {
        this.appleRefreshToken = null;
    }
```

`withdraw(...)` 메서드 본문 마지막 줄(`this.providerId = ...`) 아래에 추가:

```java
        this.appleRefreshToken = null;
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "*UserWithdrawTest" -q`
Expected: PASS (2 tests)

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/app/backend/domain/user/entity/User.java src/test/java/com/app/backend/domain/user/entity/UserWithdrawTest.java
git commit -m "feat: User에 apple_refresh_token 필드 추가(탈퇴 시 정리)"
```

---

## Task 5: 로그인/가입 DTO에 appleAuthorizationCode 추가

**Files:**
- Modify: `src/main/java/com/app/backend/domain/auth/dto/AppleLoginRequest.java`
- Modify: `src/main/java/com/app/backend/domain/auth/dto/SignupRequest.java`

- [ ] **Step 1: AppleLoginRequest 수정**

```java
package com.app.backend.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record AppleLoginRequest(
        @NotBlank String idToken,
        // 애플이 발급한 authorizationCode. 탈퇴 시 연동 해제(revoke)용 토큰 확보에 사용. 없으면 생략 가능.
        String appleAuthorizationCode
) {
}
```

- [ ] **Step 2: SignupRequest 수정**

`accessToken` 아래에 필드 추가(순서 유지):

```java
public record SignupRequest(
        @NotNull AuthProvider provider,
        @NotBlank String accessToken,
        // 애플 가입 시에만 사용. 연동 해제(revoke)용 refresh_token 확보에 쓰인다.
        String appleAuthorizationCode,
        @AssertTrue(message = "약관에 동의해야 합니다.") boolean termsAgreed
) {
}
```

- [ ] **Step 3: 컴파일 확인(호출부 시그니처 깨짐 확인용)**

Run: `./gradlew compileJava -q`
Expected: `SignupRequest` 생성자 인자 변경으로 실패할 수 있음 → Task 6에서 함께 해결. 테스트 컴파일 실패 시에도 다음 태스크에서 정리.

> 참고: `SignupRequest`를 `new`로 만드는 테스트/코드가 있으면 Task 6에서 함께 수정한다. 지금은 커밋하지 말고 Task 6까지 진행 후 함께 커밋한다.

---

## Task 6: AuthService에서 code 교환·저장 연동

**Files:**
- Modify: `src/main/java/com/app/backend/domain/auth/service/AuthService.java`
- Modify: `src/main/java/com/app/backend/domain/auth/controller/AuthController.java`
- Test: `src/test/java/com/app/backend/domain/auth/service/AuthServiceAppleTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package com.app.backend.domain.auth.service;

import com.app.backend.domain.auth.apple.AppleAuthClient;
import com.app.backend.domain.auth.dto.AuthResponse;
import com.app.backend.domain.auth.dto.SocialLoginRequest;
import com.app.backend.domain.auth.jwt.JwtProvider;
import com.app.backend.domain.auth.oauth.OAuthClient;
import com.app.backend.domain.auth.oauth.OAuthClientResolver;
import com.app.backend.domain.auth.oauth.OAuthUserInfo;
import com.app.backend.domain.auth.repository.RefreshTokenRepository;
import com.app.backend.domain.user.entity.AuthProvider;
import com.app.backend.domain.user.entity.User;
import com.app.backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServiceAppleTest {

    private OAuthClientResolver resolver;
    private UserRepository userRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private AppleAuthClient appleAuthClient;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        resolver = mock(OAuthClientResolver.class);
        userRepository = mock(UserRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        appleAuthClient = mock(AppleAuthClient.class);
        JwtProvider jwtProvider = mock(JwtProvider.class);
        when(jwtProvider.createAccessToken(any())).thenReturn("access");
        when(jwtProvider.createRefreshToken(any())).thenReturn("refresh");

        OAuthClient appleClient = mock(OAuthClient.class);
        when(appleClient.getUserInfo(any())).thenReturn(new OAuthUserInfo("apple-uid", "최예진"));
        when(resolver.resolve(AuthProvider.APPLE)).thenReturn(appleClient);

        authService = new AuthService(resolver, userRepository, jwtProvider,
                refreshTokenRepository, appleAuthClient, 2592000000L);
    }

    @Test
    void 애플로그인_code있으면_교환후_토큰저장() {
        User user = User.builder().provider(AuthProvider.APPLE).providerId("apple-uid").name("최예진").build();
        when(userRepository.findByProviderAndProviderId(AuthProvider.APPLE, "apple-uid"))
                .thenReturn(Optional.of(user));
        when(appleAuthClient.exchangeCode("CODE")).thenReturn("RT-1");

        authService.login(AuthProvider.APPLE, new SocialLoginRequest("idtoken"), "CODE");

        verify(appleAuthClient).exchangeCode("CODE");
        org.assertj.core.api.Assertions.assertThat(user.getAppleRefreshToken()).isEqualTo("RT-1");
    }

    @Test
    void 애플로그인_교환실패해도_로그인성공() {
        User user = User.builder().provider(AuthProvider.APPLE).providerId("apple-uid").name("최예진").build();
        when(userRepository.findByProviderAndProviderId(AuthProvider.APPLE, "apple-uid"))
                .thenReturn(Optional.of(user));
        when(appleAuthClient.exchangeCode(any()))
                .thenThrow(new com.app.backend.global.exception.CustomException(
                        com.app.backend.global.exception.ErrorCode.APPLE_TOKEN_EXCHANGE_FAILED));

        AuthResponse res = authService.login(AuthProvider.APPLE, new SocialLoginRequest("idtoken"), "CODE");

        org.assertj.core.api.Assertions.assertThat(res).isNotNull();
        org.assertj.core.api.Assertions.assertThat(user.getAppleRefreshToken()).isNull();
    }

    @Test
    void 카카오로그인_code없으면_애플교환_미호출() {
        OAuthClient kakao = mock(OAuthClient.class);
        when(kakao.getUserInfo(any())).thenReturn(new OAuthUserInfo("kakao-uid", "최예진"));
        when(resolver.resolve(AuthProvider.KAKAO)).thenReturn(kakao);
        User user = User.builder().provider(AuthProvider.KAKAO).providerId("kakao-uid").name("최예진").build();
        when(userRepository.findByProviderAndProviderId(AuthProvider.KAKAO, "kakao-uid"))
                .thenReturn(Optional.of(user));

        authService.login(AuthProvider.KAKAO, new SocialLoginRequest("t"), null);

        verify(appleAuthClient, never()).exchangeCode(any());
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests "*AuthServiceAppleTest" -q`
Expected: FAIL — `AuthService` 생성자/`login` 시그니처 불일치(컴파일 에러)

- [ ] **Step 3: AuthService 수정**

필드·생성자에 `AppleAuthClient` 추가하고, `login`/`signup`에 code 처리 추가.

생성자 위 필드에 추가:

```java
    private final AppleAuthClient appleAuthClient;
```

import 추가:

```java
import com.app.backend.domain.auth.apple.AppleAuthClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
```

클래스 상단에 로거 추가:

```java
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
```

생성자를 다음으로 교체:

```java
    public AuthService(OAuthClientResolver oAuthClientResolver,
                       UserRepository userRepository,
                       JwtProvider jwtProvider,
                       RefreshTokenRepository refreshTokenRepository,
                       AppleAuthClient appleAuthClient,
                       @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration) {
        this.oAuthClientResolver = oAuthClientResolver;
        this.userRepository = userRepository;
        this.jwtProvider = jwtProvider;
        this.refreshTokenRepository = refreshTokenRepository;
        this.appleAuthClient = appleAuthClient;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }
```

`login` 메서드를 다음으로 교체(파라미터 `appleAuthorizationCode` 추가):

```java
    @Transactional
    public AuthResponse login(AuthProvider provider, SocialLoginRequest request, String appleAuthorizationCode) {
        OAuthUserInfo userInfo = oAuthClientResolver.resolve(provider).getUserInfo(request.accessToken());

        Optional<User> found =
                userRepository.findByProviderAndProviderId(provider, userInfo.providerId());

        // 탈퇴한 계정은 탈퇴 시 provider_id를 비워두므로 여기서 조회되지 않는다 → 자연히 미가입(재가입=새 계정)
        if (found.isEmpty()) {
            return AuthResponse.signupRequired();
        }

        User user = found.get();
        storeAppleRefreshTokenIfPresent(provider, user, appleAuthorizationCode);
        return issueTokens(user, false);
    }
```

`signup` 메서드에서 신규/기존 유저 확보 후 code 저장 추가. 기존 `signup` 본문을 다음으로 교체:

```java
    @Transactional
    public AuthResponse signup(SignupRequest request) {
        OAuthUserInfo userInfo = oAuthClientResolver.resolve(request.provider()).getUserInfo(request.accessToken());

        Optional<User> found =
                userRepository.findByProviderAndProviderId(request.provider(), userInfo.providerId());
        if (found.isPresent()) {
            User user = found.get();
            storeAppleRefreshTokenIfPresent(request.provider(), user, request.appleAuthorizationCode());
            return issueTokens(user, true);
        }

        // 신규가입(탈퇴 후 재가입 포함). 탈퇴 계정은 provider_id를 비워둬 UNIQUE 충돌 없이 새 계정 생성.
        User user = userRepository.save(User.builder()
                .provider(request.provider())
                .providerId(userInfo.providerId())
                .name(userInfo.name())
                .build());

        storeAppleRefreshTokenIfPresent(request.provider(), user, request.appleAuthorizationCode());
        return issueTokens(user, true);
    }
```

private 헬퍼 추가(`issueTokens` 위):

```java
    // 애플 로그인이고 authorizationCode가 있으면 refresh_token으로 교환해 저장한다.
    // 교환 실패는 로그인/가입을 막지 않도록 삼키고 경고만 남긴다(연동 해제용 토큰만 미확보).
    private void storeAppleRefreshTokenIfPresent(AuthProvider provider, User user, String appleAuthorizationCode) {
        if (provider != AuthProvider.APPLE || appleAuthorizationCode == null || appleAuthorizationCode.isBlank()) {
            return;
        }
        try {
            String refreshToken = appleAuthClient.exchangeCode(appleAuthorizationCode);
            user.updateAppleRefreshToken(refreshToken);
        } catch (Exception e) {
            log.warn("애플 refresh_token 확보 실패(로그인은 계속 진행): userId={}", user.getId(), e);
        }
    }
```

- [ ] **Step 4: AuthController 수정(code 전달)**

`appleLogin`을 다음으로 교체하고, kakao/google는 `null` 전달:

```java
    @PostMapping("/kakao")
    public AuthResponse kakaoLogin(@Valid @RequestBody SocialLoginRequest request) {
        return authService.login(AuthProvider.KAKAO, request, null);
    }

    @PostMapping("/google")
    public AuthResponse googleLogin(@Valid @RequestBody SocialLoginRequest request) {
        return authService.login(AuthProvider.GOOGLE, request, null);
    }

    @PostMapping("/apple")
    public AuthResponse appleLogin(@Valid @RequestBody AppleLoginRequest request) {
        return authService.login(AuthProvider.APPLE,
                new SocialLoginRequest(request.idToken()), request.appleAuthorizationCode());
    }
```

- [ ] **Step 5: 기존 호출부/테스트 수정**

`SignupRequest`를 `new`로 생성하거나 `authService.login(provider, request)`를 호출하는 기존 코드/테스트가 있으면 새 시그니처에 맞춘다. 먼저 찾는다:

Run: `grep -rn "new SignupRequest(\|authService.login(" src`
각 사용처에 `appleAuthorizationCode` 인자(비애플이면 `null`)를 추가한다.

- [ ] **Step 6: 테스트 통과 + 전체 컴파일 확인**

Run: `./gradlew test --tests "*AuthServiceAppleTest" compileTestJava -q`
Expected: PASS (3 tests), 컴파일 성공

- [ ] **Step 7: 커밋(Task 5 변경 포함)**

```bash
git add src/main/java/com/app/backend/domain/auth/dto/AppleLoginRequest.java \
        src/main/java/com/app/backend/domain/auth/dto/SignupRequest.java \
        src/main/java/com/app/backend/domain/auth/service/AuthService.java \
        src/main/java/com/app/backend/domain/auth/controller/AuthController.java \
        src/test/java/com/app/backend/domain/auth/service/AuthServiceAppleTest.java
git commit -m "feat: 애플 로그인 시 authorizationCode 교환해 refresh_token 저장"
```

---

## Task 7: 탈퇴 시 revoke 호출

**Files:**
- Modify: `src/main/java/com/app/backend/domain/user/service/UserService.java`
- Test: `src/test/java/com/app/backend/domain/user/service/UserServiceWithdrawTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package com.app.backend.domain.user.service;

import com.app.backend.domain.auth.apple.AppleAuthClient;
import com.app.backend.domain.auth.repository.RefreshTokenRepository;
import com.app.backend.domain.group.repository.MembershipRepository;
import com.app.backend.domain.notification.repository.NotificationRepository;
import com.app.backend.domain.user.entity.AuthProvider;
import com.app.backend.domain.user.entity.User;
import com.app.backend.domain.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserServiceWithdrawTest {

    private UserRepository userRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private AppleAuthClient appleAuthClient;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        appleAuthClient = mock(AppleAuthClient.class);
        userService = new UserService(userRepository, new ObjectMapper(), refreshTokenRepository,
                mock(NotificationRepository.class), mock(MembershipRepository.class),
                appleAuthClient, "https://cdn.example/");
    }

    private User apple(String rt) {
        User u = User.builder().provider(AuthProvider.APPLE).providerId("uid").name("최예진").build();
        if (rt != null) u.updateAppleRefreshToken(rt);
        return u;
    }

    @Test
    void 애플유저_토큰있으면_revoke호출() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(apple("RT-1")));
        userService.withdraw(1L);
        verify(appleAuthClient).revoke("RT-1");
    }

    @Test
    void revoke실패해도_탈퇴진행() {
        User user = apple("RT-1");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        doThrow(new com.app.backend.global.exception.CustomException(
                com.app.backend.global.exception.ErrorCode.APPLE_REVOKE_FAILED))
                .when(appleAuthClient).revoke(any());

        userService.withdraw(1L);

        org.assertj.core.api.Assertions.assertThat(user.isWithdrawn()).isTrue();
        verify(refreshTokenRepository).deleteByUserId(1L);
    }

    @Test
    void 비애플유저_revoke미호출() {
        User u = User.builder().provider(AuthProvider.KAKAO).providerId("uid").name("최예진").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        userService.withdraw(1L);
        verify(appleAuthClient, never()).revoke(any());
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests "*UserServiceWithdrawTest" -q`
Expected: FAIL — `UserService` 생성자 시그니처 불일치

- [ ] **Step 3: UserService 수정**

import 추가:

```java
import com.app.backend.domain.auth.apple.AppleAuthClient;
import com.app.backend.domain.user.entity.AuthProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
```

로거 + 필드 추가:

```java
    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private final AppleAuthClient appleAuthClient;
```

생성자 파라미터에 `AppleAuthClient appleAuthClient`를 추가하고 `this.appleAuthClient = appleAuthClient;` 대입(기존 파라미터 순서 유지, `@Value` profileImageUrlPrefix 앞에 삽입).

`withdraw` 메서드를 다음으로 교체:

```java
    @Transactional
    public void withdraw(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 애플 로그인 유저는 탈퇴 시 애플 연동 해제(revoke). 실패해도 탈퇴는 계속 진행.(App Store 심사 규정)
        if (user.getProvider() == AuthProvider.APPLE && user.getAppleRefreshToken() != null) {
            try {
                appleAuthClient.revoke(user.getAppleRefreshToken());
            } catch (Exception e) {
                log.warn("애플 연동 해제 실패(탈퇴는 계속 진행): userId={}", userId, e);
            }
        }

        // soft delete + 익명화 + provider_id 자리 비움(재가입 가능). 데이터(알림·멤버십)는 5일 보존.
        user.withdraw(LocalDateTime.now());
        // refresh token은 보안상 즉시 폐기(세션 종료)
        refreshTokenRepository.deleteByUserId(userId);
    }
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "*UserServiceWithdrawTest" -q`
Expected: PASS (3 tests)

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/app/backend/domain/user/service/UserService.java src/test/java/com/app/backend/domain/user/service/UserServiceWithdrawTest.java
git commit -m "feat: 탈퇴 시 애플 로그인 연동 해제(revoke) 호출"
```

---

## Task 8: 설정·키 배치·전체 검증

**Files:**
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/resources/application-dev.yml`
- Modify: `.gitignore`
- Add(비커밋): `src/main/resources/apple/AuthKey_HBT5YX26FZ.p8`

- [ ] **Step 1: .gitignore에 .p8 추가**

`*-service-account.json` 아래에 추가:

```
# 애플 로그인 개인키 (절대 커밋 금지)
src/main/resources/apple/*.p8
*.p8
```

- [ ] **Step 2: application.yml에 apple 설정 추가**

`aws:` 블록 아래(파일 끝)에 추가:

```yaml
apple:
  team-id: ${APPLE_TEAM_ID:3PVV8DQPL6}
  key-id: ${APPLE_KEY_ID:HBT5YX26FZ}
  # iOS가 네이티브 애플 로그인이면 Bundle ID, 웹 플로우면 Service ID(com.swyp.ddara.service)로 교체
  client-id: ${APPLE_CLIENT_ID:com.ddara.team3}
  private-key-path: ${APPLE_PRIVATE_KEY_PATH:apple/AuthKey_HBT5YX26FZ.p8}
```

(application-dev.yml에도 동일 블록 추가 — 운영 환경변수로 덮어쓸 수 있게)

- [ ] **Step 3: 개인키 파일 배치(커밋 안 됨)**

```bash
mkdir -p src/main/resources/apple
cp ~/Downloads/AuthKey_HBT5YX26FZ.p8 src/main/resources/apple/AuthKey_HBT5YX26FZ.p8
git status --short   # .p8이 목록에 안 보이면 정상(gitignore 적용)
```

Expected: `AuthKey_HBT5YX26FZ.p8`가 `git status`에 나타나지 않음.

- [ ] **Step 4: 전체 빌드·테스트**

Run: `./gradlew build -q`
Expected: BUILD SUCCESSFUL, 모든 테스트 통과

- [ ] **Step 5: 커밋**

```bash
git add src/main/resources/application.yml src/main/resources/application-dev.yml .gitignore
git commit -m "chore: 애플 로그인 revoke 설정 추가 및 .p8 gitignore"
```

---

## Task 9: 마무리 — iOS 연동 규격 전달 + 문서

- [ ] **Step 1: 스펙의 "iOS 팀에 전달할 연동 규격" 섹션을 도윤/광진님에게 공유**
  - 로그인/가입 요청 바디에 `appleAuthorizationCode` 추가 전송
  - 애플 로그인이 네이티브(`ASAuthorizationController`)인지 확인 → 아니면 `apple.client-id`를 Service ID로 교체
  - `authorizationCode`는 1회용·5분 유효 → 로그인마다 새 값 전송

- [ ] **Step 2: PR 생성**
  - [[github-workflow]] 규칙대로 **이슈 먼저 생성** 후 그 이슈 기반 PR.
  - **PR 대상 브랜치는 `develop`** ([[integration-branch-is-develop]], `main` 아님).
  - Swagger(`springdoc`) 문서에 `appleAuthorizationCode` 필드가 반영됐는지 확인.

---

## Self-Review 체크

- 스펙의 각 컴포넌트(생성기·클라이언트·엔티티·DTO·로그인연동·탈퇴연동·설정) → Task 1~8로 모두 커버.
- 에러 정책(교환 실패 시 로그인 계속 / revoke 실패 시 탈퇴 계속 / 키 부재 no-op) → Task 2·6·7 테스트로 검증.
- 타입 일관성: `updateAppleRefreshToken`/`clearAppleRefreshToken`/`getAppleRefreshToken`, `exchangeCode`/`revoke`, `getClientId`/`generate` 전 태스크 동일 사용.
- YAGNI: refresh_token 암호화·Firebase 제거 리팩터링은 범위 밖(스펙과 일치).
