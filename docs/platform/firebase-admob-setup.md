# Firebase · AdMob 연결 (21장 구현 1단계)

코드는 준비됨. 아래 콘솔 작업 결과물(설정 파일 1개, ID 2개)만 넣으면 켜진다. 넣기 전에도 앱은 지금처럼 빌드·실행된다(Firebase 꺼짐, 광고는 Google 테스트 광고).

## 패키지 이름

| 빌드 | 패키지 |
|---|---|
| 출시(release) | `com.graviton94.notificationcleaner` |
| 테스트(debug, CI APK) | `com.graviton94.notificationcleaner.playtest` |

## 1. Firebase 프로젝트

1. Firebase 콘솔 → 프로젝트 추가 → 이름 예: `notification-cleaner` → Google 애널리틱스 **사용**
2. Android 앱 추가 2번(위 표의 패키지 둘 다). 앱 닉네임은 자유, SHA-1은 지금 비워도 됨(카카오·구글 로그인 붙일 때 추가)
3. `google-services.json` 다운로드 — 두 앱을 다 추가한 뒤 받으면 한 파일에 둘 다 들어 있음
4. 넣는 방법 중 하나
   - GitHub 저장소 → Settings → Secrets and variables → Actions → **New repository secret**, 이름 `GOOGLE_SERVICES_JSON`, 값 = 파일 내용 전체 (권장: CI APK에 자동 반영)
   - 또는 `app/google-services.json`으로 커밋 (비공개 저장소일 때만)
5. 콘솔에서 켤 것: Crashlytics(시작하기), Authentication(나중에 카카오 연동 시), Firestore(나중에 클라우드 저장 시), Remote Config

## 2. AdMob

1. AdMob 콘솔 → 앱 → 앱 추가 → Android → "아직 스토어에 없음" → 이름 `알림 청소부`
2. 앱 설정에서 **Firebase에 연결**(위 프로젝트)
3. 광고 단위 → **보상형** 1개 만들기 (이름 예: `rewarded_main`). 지면 구분은 게임이 이벤트로 기록하므로 단위는 하나로 시작
4. 받은 값 두 개를 알려 주거나 `gradle.properties`에 추가
   ```
   ADMOB_APP_ID=ca-app-pub-xxxxxxxxxxxxxxxx~yyyyyyyyyy
   ADMOB_REWARDED_ID=ca-app-pub-xxxxxxxxxxxxxxxx/zzzzzzzzzz
   ```
   공개해도 되는 값(비밀 아님). **디버그 빌드는 항상 테스트 광고**, 실제 ID는 출시 빌드에만 쓰인다(무효 트래픽 방지)
5. 개인정보 보호 및 메시지 → **GDPR 동의 메시지** 만들기(UMP). 앱은 실행 때 필요한 지역에서만 동의 창을 띄운다

## 3. 코드에서 하는 일

| 파일 | 역할 |
|---|---|
| `app/build.gradle.kts` | Firebase BoM(Analytics·Crashlytics·Auth·Firestore·Remote Config), AdMob, UMP. `google-services.json`이 있을 때만 Firebase 플러그인 적용 |
| `platform/Ads.kt` | UMP 동의 → AdMob 초기화 → 보상형 1개 미리 로드 → 표시 → 보상 여부만 페이지에 알림(지급은 core) |
| `platform/Telemetry.kt` | Firebase Analytics 이벤트(없으면 무시) |
| `GameBridge.showAd / log` | 페이지 `window.Game.showAd(id)` → `window.onAdResult(id, earned, reason)` |
| CI | 시크릿 `GOOGLE_SERVICES_JSON`이 있으면 빌드 전에 파일로 기록 |

## 4. 다음 단계(로그인·서버)

- 카카오 로그인: Kakao Developers 앱 생성 → 네이티브 앱 키, 키 해시 등록 → Firebase Auth와 연결(OIDC 또는 커스텀 토큰 서버)
- 계정 저장: Firestore `users/{uid}`에 세이브(SaveCodec JSON) · 장착 칭호 · 가이드 진행 · 복귀 횟수
- 일일 도전 랭킹: Firestore `daily/{day}/scores` + 티어별 보상(설계 보완 예정)
- Play Console 데이터 보안: 광고 ID 수집(AdMob), 진단·분석(Firebase) 항목 추가 필요
