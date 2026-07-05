# 알림 공용 이미지 (assets)

알림 목록(NOTI-01)에 쓰는 **앱 로고**를 담아둔 폴더다.

- `ddara-logo.png` — 모임 관련 알림(모임 참여·마감 임박)의 `payload.imageUrl`로 나가는 앱 로고. (원본 4096×4096을 512×512로 리사이즈)
- 개인 관련 알림(새 따라찍기 시작·따라찍기 완료)은 **스타터가 직접 올린 프로필 이미지**(`users.profile_image_url`)를 쓰므로 여기 파일이 필요 없다.

## S3에 올리는 법 (⚠️ 1회만, AWS 권한 필요 — 오지원 인프라)

코드(`NotificationService`)는 아래 주소만 참조한다. 실제로 이 주소에서 이미지가 떠야 알림에 보인다.

```
https://ddara-images.s3.ap-northeast-2.amazonaws.com/assets/ddara-logo.png
```

버킷·리전은 `application.yml`의 `aws.s3.bucket`(기본 `ddara-images`) / `aws.s3.region`(기본 `ap-northeast-2`)과 자동으로 맞춰진다.

### 방법 A — AWS CLI로 직접 업로드 (권한 있는 사람이 1회 실행)
```bash
aws s3 cp docs/assets/ddara-logo.png \
  s3://ddara-images/assets/ddara-logo.png \
  --content-type image/png
```
> 버킷이 퍼블릭 읽기가 아니면 앱처럼 안 보인다. shots/profiles와 동일한 공개 정책(오지원 세팅)을 `assets/` 경로에도 적용하면 된다.

### 방법 B — presign 발급으로 업로드
`UploadService.PURPOSE_TO_DIR`에 `"asset" → "assets"`를 추가한 뒤,
`POST /api/uploads/presign`(purpose=asset)으로 업로드 URL을 받아 S3에 PUT 한다.
(shots/profiles 업로드와 동일한 2-step 방식.)
