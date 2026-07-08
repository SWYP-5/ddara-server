# Notion API 명세 반영 목록 — 애플 로그인 연동 해제(탈퇴 시 revoke)

> 작성일 2026-07-08 / 브랜치 `feat/apple-revoke-on-withdrawal`
> 아래 3개 API를 노션 명세에 반영. 프론트(iOS) 협조 필요 항목은 맨 아래 별도 정리.
> 관련: App Store 심사 규정 5.1.1(v) — 애플로 가입한 사용자는 탈퇴 시 애플 연동도 해제해야 함.

---

## 1. `POST /api/auth/apple` — 애플 로그인  ✏️ 수정

**변경점:** 요청 바디에 `appleAuthorizationCode`(선택) 필드 추가.

### Request Body (변경 후)
| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `idToken` | string | ✅ | 기존과 동일(Firebase ID 토큰) |
| `appleAuthorizationCode` | string | ❌ (신규) | 애플이 발급한 authorizationCode. 탈퇴 시 연동 해제(revoke)에 쓸 refresh_token 확보용. 없으면 무시(기존 동작 유지) |

```json
{
  "idToken": "<firebase_id_token>",
  "appleAuthorizationCode": "<apple_authorization_code>"
}
```

**동작:** `appleAuthorizationCode`가 오면 백엔드가 애플과 교환해 refresh_token을 저장한다.
교환에 실패해도 **로그인은 정상 성공**한다(연동 해제용 토큰만 미확보, 경고 로그만 남김).
응답 스펙 변화 없음.

---

## 2. `POST /api/auth/signup` — 회원가입  ✏️ 수정

**변경점:** 요청 바디에 `appleAuthorizationCode`(선택) 필드 추가. **애플 가입 시에만** 사용.

### Request Body (변경 후)
| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `provider` | string(enum) | ✅ | KAKAO / GOOGLE / APPLE |
| `accessToken` | string | ✅ | 기존과 동일 |
| `appleAuthorizationCode` | string | ❌ (신규) | 애플 가입 시에만. revoke용 refresh_token 확보에 사용 |
| `termsAgreed` | boolean | ✅ | 기존과 동일 |

```json
{
  "provider": "APPLE",
  "accessToken": "<firebase_id_token>",
  "appleAuthorizationCode": "<apple_authorization_code>",
  "termsAgreed": true
}
```

**동작:** 애플 가입이고 code가 있으면 refresh_token 저장. 교환 실패해도 가입은 정상 진행. 응답 스펙 변화 없음.

---

## 3. `DELETE /api/users/me` — 회원 탈퇴  ⚠️ 동작 변경(스키마 변화 없음)

**요청/응답 스펙 변화 없음** (기존대로 인증 필요, 204 No Content).

**추가 동작:** 탈퇴 사용자가 **애플로 가입**했고 저장된 refresh_token이 있으면,
백엔드가 애플 `POST https://appleid.apple.com/auth/revoke`를 호출해 **Sign in with Apple 연동을 해제**한다.
- revoke 실패(네트워크 오류 등)해도 **탈퇴는 정상 완료**된다(경고 로그만).
- 애플이 아닌 사용자/토큰이 없는 사용자는 revoke를 건너뛴다.

> 명세 비고란에 "애플 유저 탈퇴 시 애플 연동 해제(revoke) 자동 수행" 한 줄 추가 권장.

---

## 프론트(iOS) 협조 필요 — 명세 "연동 조건"에 명시

1. **애플 로그인/가입 요청 시 `appleAuthorizationCode`를 함께 전송**해야 탈퇴 시 연동 해제가 가능함.
   보내지 않으면 revoke를 못 해 **심사에서 리젝될 수 있음**.
2. `authorizationCode`는 **1회용·약 5분 유효** → 매 로그인마다 새로 발급된 값을 전송.
3. 애플 로그인 방식이 **네이티브(`ASAuthorizationController`)**인지 확인 필요.
   - 네이티브 → `client_id` = Bundle ID `com.ddara.team3` (현재 기본값)
   - 웹 플로우 → `client_id` = Service ID `com.swyp.ddara.service` (백엔드 설정 `APPLE_CLIENT_ID`로 교체)

## 배포/운영 메모 (백엔드)

- 애플 개인키 `AuthKey_HBT5YX26FZ.p8`는 **커밋 금지(gitignore)** → 서버에 별도 배치 필요
  (`src/main/resources/apple/AuthKey_HBT5YX26FZ.p8` 또는 환경변수 `APPLE_PRIVATE_KEY_PATH`).
- 설정값(기본값 내장, 필요 시 환경변수로 덮어쓰기): `APPLE_TEAM_ID`, `APPLE_KEY_ID`, `APPLE_CLIENT_ID`, `APPLE_PRIVATE_KEY_PATH`.
- `apple_refresh_token` 컬럼이 `users` 테이블에 추가됨(JPA `ddl-auto: update`로 자동 반영).
