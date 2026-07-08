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
        Instant now = Instant.now();

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
