# 애플 로그인 연동 해제 (탈퇴 시 Sign in with Apple revoke) — 설계

- 작성일: 2026-07-08
- 관련: U-05(회원 탈퇴), 애플 App Store 심사 규정 5.1.1(v)

## 배경 / 요구사항

App Store 심사 규정상, **Sign in with Apple로 가입한 사용자가 회원 탈퇴하면 백엔드가
애플의 연동 해제(revoke) API를 호출**해서 사용자 애플 ID의 "이 앱으로 로그인" 목록에서
우리 앱을 제거해야 한다. 이를 구현하지 않으면 앱 심사에서 리젝된다.

- 해제 엔드포인트: `POST https://appleid.apple.com/auth/revoke`
- 호출에는 `client_secret`(애플이 발급한 `.p8` 개인키로 서명한 JWT)과
  **해제할 애플 토큰(refresh_token)**이 필요하다.

## 현재 구조와 걸림돌

- 애플 로그인은 **Firebase Auth를 경유**한다. 앱은 애플 로그인 후 백엔드에
  **Firebase ID 토큰**(`AppleLoginRequest.idToken`)만 보내고, 백엔드는 Firebase Admin SDK로
  검증해 `uid`·`name`만 얻는다. → 백엔드는 **애플이 발급한 토큰을 전혀 받지 못한다.**
- revoke는 해제 대상 토큰이 있어야 호출 가능하므로, 현재 구조로는 재료 자체가 없다.

### 해결: iOS가 authorizationCode를 백엔드로 전달 (팀 간 의존)

네이티브 애플 로그인 시 iOS는 `ASAuthorizationAppleIDCredential.authorizationCode`를 받는다.
앱이 이 값을 로그인/가입 요청에 함께 백엔드로 보내면, 백엔드가 애플과 교환해
refresh_token을 저장할 수 있다. **iOS 앱 변경이 선행 조건**이며, 백엔드를 먼저 구현하고
iOS에 정확한 연동 규격을 전달한다.

## 애플이 발급한 인증 정보 (백엔드가 사용)

| 항목 | 값 | 용도 |
|---|---|---|
| Team ID | `3PVV8DQPL6` | client_secret JWT의 `iss` |
| Key ID | `HBT5YX26FZ` | client_secret JWT 헤더 `kid` |
| 개인키 파일 | `AuthKey_HBT5YX26FZ.p8` | ES256 서명 (커밋 금지, gitignore) |
| client_id | `com.ddara.team3` (Bundle ID, 기본값·설정으로 교체 가능) | JWT `sub`, 토큰/해제 요청의 `client_id` |
| Service ID | `com.swyp.ddara.service` | (웹 플로우일 경우의 client_id 후보) |

> `.p12`, `.mobileprovision`은 iOS 앱 빌드/서명용으로 백엔드에서 사용하지 않는다.

## 전체 흐름

```
[로그인/가입 시]
 iOS 앱 ──authorizationCode──▶ 백엔드
        └ APPLE + code 존재 시:
            client_secret(JWT) 서명 → POST /auth/token (grant_type=authorization_code)
            → 애플이 refresh_token 발급 → User.apple_refresh_token 저장(덮어쓰기)

[탈퇴 시] (UserService.withdraw)
 User가 APPLE + apple_refresh_token 보유 시:
    client_secret(JWT) 서명 → POST /auth/revoke (token_type_hint=refresh_token)
    → 성공/실패와 무관하게 토큰 필드 정리 후 기존 탈퇴(soft delete) 진행
```

## 컴포넌트

1. **`AppleClientSecretGenerator`**
   - `.p8`(PKCS#8 PEM)을 자바 `KeyFactory("EC")` + `PKCS8EncodedKeySpec`으로 `PrivateKey`로 파싱.
   - `jjwt`로 ES256 JWT 생성: header `kid`=Key ID / claims `iss`=Team ID, `iat`=now,
     `exp`=now+5분, `aud`=`https://appleid.apple.com`, `sub`=client_id.
   - 입력: 없음(설정 주입). 출력: `String clientSecret`.

2. **`AppleAuthClient`** (Spring `RestClient`)
   - `String exchangeCode(String authorizationCode)` → `refresh_token` 반환
     (`POST /auth/token`, `application/x-www-form-urlencoded`:
      `grant_type=authorization_code`, `code`, `client_id`, `client_secret`).
   - `void revoke(String refreshToken)`
     (`POST /auth/revoke`: `client_id`, `client_secret`, `token`, `token_type_hint=refresh_token`).
   - 실패 시 명확한 예외(`CustomException`)로 변환. revoke 실패는 상위에서 삼켜 로그만 남긴다.

3. **설정** (`AppleAuthProperties` / `application-*.yml`)
   ```yaml
   apple:
     team-id: ${APPLE_TEAM_ID:3PVV8DQPL6}
     key-id: ${APPLE_KEY_ID:HBT5YX26FZ}
     client-id: ${APPLE_CLIENT_ID:com.ddara.team3}
     private-key-path: ${APPLE_PRIVATE_KEY_PATH:apple/AuthKey_HBT5YX26FZ.p8}
   ```
   - `.p8`는 `src/main/resources/apple/` 아래 두되 **gitignore**(Firebase 키와 동일 패턴).
   - 키가 없는 환경(CI 등)에서는 애플 기능만 비활성/스킵하고 앱은 정상 기동.

4. **`User` 엔티티**
   - `@Column(name = "apple_refresh_token") String appleRefreshToken` 추가(길이 여유 있게).
   - `updateAppleRefreshToken(String)`, `clearAppleRefreshToken()` 메서드.
   - `withdraw(...)` 시 필드 정리(개인정보 익명화 흐름과 함께).
   - **로그·JSON 응답에 노출 금지** (getter가 응답 DTO로 새지 않도록 주의).

5. **로그인/가입 경로**
   - `AppleLoginRequest`에 `String appleAuthorizationCode`(선택) 추가.
   - `SignupRequest`(애플) 경로에도 code 전달 가능하게 확장.
   - `AuthService`: 로그인·가입에서 provider가 APPLE이고 code가 있으면
     `exchangeCode` → `user.updateAppleRefreshToken(...)`. code가 없으면(기존 앱/타 provider) 스킵.

6. **탈퇴 경로** (`UserService.withdraw`)
   - `user`가 APPLE이고 `appleRefreshToken`이 있으면 `appleAuthClient.revoke(...)` 시도.
   - try/catch로 감싸 **실패해도 탈퇴 진행**(로그 warn). 이후 토큰 필드 정리.

## 에러 처리 정책

- **토큰 교환 실패**(로그인/가입): 로그인 자체를 막지 않도록 code 교환 실패는 warn 로그만 남기고
  로그인은 성공 처리(연동 해제용 토큰만 미저장). — 로그인 UX 우선.
- **revoke 실패**(탈퇴): 계정 삭제가 막히면 안 되므로 warn 로그 후 탈퇴 계속.
- **키 부재**: 애플 설정/키가 없으면 관련 기능만 no-op, 앱 기동은 정상.

## 테스트

- `AppleClientSecretGenerator`: 생성된 JWT의 header/claims(alg=ES256, kid, iss, aud, sub) 검증,
  공개키로 서명 검증.
- `AppleAuthClient`: `MockRestServiceServer` 등으로 애플 응답 목킹 →
  exchange 정상/실패, revoke 정상/실패.
- 탈퇴 통합: 애플+토큰 유저 탈퇴 시 revoke 호출 및 토큰 정리 확인, revoke 실패해도 탈퇴 완료 확인,
  비애플/토큰없음 유저는 revoke 미호출.

## iOS 팀에 전달할 연동 규격 (핸드오프)

1. 애플 로그인 방식이 **네이티브(`ASAuthorizationController`)**인지 확인
   (그래야 `authorizationCode`를 앱이 직접 확보 가능). 웹 플로우면 client_id가
   Service ID(`com.swyp.ddara.service`)가 되므로 설정값을 교체해야 함.
2. 로그인/가입 API 요청 바디에 `appleAuthorizationCode`(문자열) 추가 전송.
3. `authorizationCode`는 **1회용·단시간(약 5분)** 유효 → 로그인 시마다 새로 발급된 값 전송.

## 범위 밖 (YAGNI)

- refresh_token 암호화 저장(추후 개선 항목으로만 기록).
- Firebase 없이 백엔드가 애플 로그인 전체를 직접 처리하는 리팩터링(현 구조 유지).
