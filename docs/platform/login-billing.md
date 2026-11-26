# 로그인 · 클라우드 저장 · 결제 (11-26)

게임은 **게스트로 끝까지 플레이 가능**. 로그인은 진행 백업·기기 이동용. 결제는 Google Play 결제만.

## 1. 구조

| 부분 | 어디 | 하는 일 |
|---|---|---|
| Google 로그인 | `platform/Account.kt` | Credential Manager → ID 토큰 → Firebase Auth. 웹 클라이언트 ID는 google-services.json의 `default_web_client_id` |
| 카카오 로그인 | `platform/Account.kt` + `functions/main.py` `login` | 카카오 SDK 액세스 토큰 → Cloud Function이 카카오에 확인 → Firebase 커스텀 토큰(uid `kakao:<회원번호>`). 다른 카카오 앱 토큰 거부(`KAKAO_APP_ID`) |
| 클라우드 저장 | Firestore `users/{uid}` = `{save, level, savedAt, app}` | 로그인 직후: 클라우드 없음 → 이 폰 진행 업로드 / 이 폰이 새 게임 → 클라우드 불러오기 / 둘 다 있음 → 플레이어가 고름. 이후 3분마다·앱 나갈 때 백업 |
| 계정 삭제 | 설정 › 계정 › 계정 삭제(두 번 누르기) → `deleteAccount` | 클라우드 저장과 Firebase 사용자 삭제. 이 폰 게임은 게스트로 남음 (Play 정책: 앱 안 삭제) |
| 규칙 | `firestore.rules` | `users/{uid}`는 본인만, 필드·크기 제한, 나머지 전부 금지 |
| 결제 | `platform/Billing.kt` (Play Billing 8) | 페이지 `Game.purchase` → Play 결제창 → **코어가 지급**(`WebSession.buy*`, claim id 1회) → 소비(일회성) / 확인(구독). 앱 재시작·복귀 때 미처리 결제 복구 |
| 가격 표시 | `Game.store()` | Play 현지 가격(₩, $ …)을 화면에 표시. Billing이 없으면(브라우저·개발) 기존 테스트 지급 |

claim id: 일회성 = `play:<purchaseToken>`, 구독 = `play:<orderId>`(갱신마다 새 주문 번호 → 30일씩 지급).

## 2. 상품 (`tools/play/products.json` ↔ `Billing.kt PRODUCTS`)

| Play ID | 종류 | ₩ | 코어 |
|---|---|---|---|
| data_100 · data_540 · data_1200 · data_3700 · data_8600 · data_16800 | 일회성(소비) | 2,200 · 5,900 · 12,000 · 33,000 · 69,000 · 119,000 | buyData |
| starter_pack | 일회성 | 2,500 | buyStarter (설치 후 48시간) |
| rookie_pass | 일회성 | 3,300 | buyRookie (7일 매일 지급) |
| pass_monthly · pass_turbo · pass_ultra | 구독 P1M, 기본 요금제 `monthly` | 5,500 · 33,000 · 99,000 | buyPass (상위 패스는 차액 결제로 교체) |

가격·구성을 바꾸면 밸런스 세션이 코어 수치와 이 표를 함께 갱신 → `Play products` 워크플로 다시 실행.

## 3. 배포 · 실행 순서

1. **Functions** (`.github/workflows/functions.yml`, 푸시 때 자동): Kakao `login`, `deleteAccount`, Firestore 규칙.
   - 서비스 계정 권한 추가 필요: **서비스 사용량 관리자(Service Usage Admin)** — API 사용 설정용.
   - `FIREBASE_SERVICE_ACCOUNT`와 앱의 `GOOGLE_SERVICES_JSON`이 같은 Firebase 프로젝트여야 함(워크플로가 검사).
   - 카카오 로그인용: 함수 실행 계정(`<프로젝트번호>-compute@developer.gserviceaccount.com`)에 **서비스 계정 토큰 생성자** 권한(워크플로가 경고로 알려 줌).
2. **AAB** (`android-aab.yml`): 결제 권한 포함 빌드 → 사용자가 Play Console 내부/비공개 테스트에 **첫 업로드**.
3. **상품 등록** (`Play products` 워크플로, 수동 실행): 위 표 그대로 생성·갱신, 구독 기본 요금제 활성화.
4. **Play 앱 서명 키 해시**를 카카오 개발자 콘솔에, SHA-1을 Firebase에 추가 (첫 업로드 뒤 Play Console › 앱 무결성).
5. 라이선스 테스터(Play Console › 설정 › 라이선스 테스트)에 테스트 계정 추가 → 실제 결제 없이 결제 흐름 시험.
