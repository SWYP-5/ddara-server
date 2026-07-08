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
        } catch (CustomException e) {
            throw e;
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
