# ddara-server
  
  Spring Boot 기반 백엔드 서버입니다.
  
  ---

## 🛠 기술 스택

**Backend**
- Java 21, Spring Boot 3.5.14
- Spring Data JPA (Hibernate)

**Database**
- MySQL 8.4

**Docs & Etc.**
- Swagger (springdoc-openapi 2.8.6)
- FCM (firebase-admin 9.3.0)

**Infra / DevOps**
- AWS EC2, Nginx
- Docker
- GitHub Actions (CI / CD)

  ---

## 🚀 로컬 실행

### 사전 준비
- JDK 21
- Docker (OrbStack 또는 Docker Desktop) — 설치 후 데몬 실행
- IntelliJ IDEA

### 실행

  ```bash
  # 1. 클론
  git clone https://github.com/ddara-app/ddara-server.git
  cd ddara-server
  
  # 2. 로컬 MySQL 실행 (Docker)
  docker compose up -d

  # 3. 서버 실행
  ./gradlew bootRun
  ```
  
로컬 DB는 Docker로 통일됩니다. (`app_db` / `root` / `1234` / `3306`)
별도 프로파일 설정 없이 바로 실행되며, 접속 정보·시크릿은 배포 시 환경변수로 덮어씁니다.

### 확인  
- 헬스체크: http://localhost:8080/api/health
- Swagger UI: http://localhost:8080/swagger-ui/index.html

---

## 📁 프로젝트 구조

도메인형 패키지 구조입니다.
  
  ```
  src/main/
  ├── java/com/app/backend/
  │   ├── AppBackendApplication.java   # 진입점
  │   ├── domain/          # 도메인별 기능
  │   │   ├── auth/        # 인증 (로그인, JWT, OAuth)
  │   │   ├── user/        # 회원
  │   │   └── {도메인}/
  │   │       ├── controller/      # API 엔드포인트
  │   │       ├── service/         # 비즈니스 로직
  │   │       ├── repository/      # DB 접근 (JPA)
  │   │       ├── entity/          # 엔티티
  │   │       └── dto/             # 요청·응답 객체
  │   └── global/          # 공통 (설정, 예외 처리, 헬스체크)
  └── resources/
      ├── application.yml              # 애플리케이션 설정
      └── sql/schema.sql              # DB 초기화 SQL
  ```

---

## 🌿 협업 규칙

- 브랜치: `{타입}/{이슈번호}-{설명}` (예: `feat/7-oauth-client`)
- 타입: `feat`, `fix`, `chore`, `docs` 등
- 작업은 이슈 생성 → 브랜치 → PR → `develop` 머지 순서로 진행합니다.

---

## 💬 커밋 컨벤션

```
type: 제목 (#이슈번호) 
```
(예: `feat: 카카오 로그인 구현 #7`)

| Type | 설명 |
|------|------|
| `feat` | 새로운 기능 |
| `fix` | 버그 수정 |
| `refactor` | 리팩토링 (기능 변화 없음) |
| `docs` | 문서 수정 |
| `test` | 테스트 추가/수정 |
| `chore` | 빌드·설정·기타 잡일 |

---

## 배포

`develop` 브랜치에 머지되면 GitHub Actions(`.github/workflows/deploy.yml`)가
AWS EC2에 자동으로 빌드·배포하고 헬스체크까지 수행합니다.

---

## 👥 팀

| 이름 | 역할 | GitHub |
|------|------|--------|
| 오지원| Backend | [@ohjw26](https://github.com/ohjw26) |
| 최예진 | Backend | [@yejinchoi24](https://github.com/yejinchoi24) |
