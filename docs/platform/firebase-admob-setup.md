# Firebase · AdMob 연결 (21장 구현 1단계)

코드는 준비됨. 아래 콘솔 작업 결과물(설정 파일 1개, ID 2개)만 넣으면 켜진다. 넣기 전에도 앱은 지금처럼 빌드·실행된다(Firebase 꺼짐, 광고는 Google 테스트 광고).

## 패키지 이름

`io.github.graviton94.donotdisturb` — 다른 앱(`io.github.graviton94.todayletters`, `io.github.graviton94.todaybible`)과 같은 규칙. 테스트(debug)와 출시 빌드가 같은 패키지라 Firebase에는 **앱 1개만** 등록하면 된다.

## 1. Firebase 프로젝트

1. Firebase 콘솔 → 프로젝트 추가 → 이름 예: `do-not-disturb` → Google 애널리틱스 **사용**
2. Android 앱 추가: 패키지 `io.github.graviton94.donotdisturb`. 앱 닉네임은 자유, SHA-1은 지금 비워도 됨(카카오·구글 로그인 붙일 때 추가)
3. `google-services.json` 다운로드
4. 넣는 방법 중 하나
   - GitHub 저장소 → Settings → Secrets and variables → Actions → **New repository secret**, 이름 `GOOGLE_SERVICES_JSON`, 값 = 파일 내용 전체 (권장: CI APK에 자동 반영)
   - 또는 `app/google-services.json`으로 커밋 (비공개 저장소일 때만)
5. 콘솔에서 켤 것: Crashlytics(시작하기), Authentication(나중에 카카오 연동 시), Firestore(나중에 클라우드 저장 시), Remote Config

## 2. AdMob

1. AdMob 콘솔 → 앱 → 앱 추가 → Android → "아직 스토어에 없음" → 이름 `Do Not Disturb!`
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

## 5. 서명 키 지문 (Firebase SHA)

| 키 | SHA-1 | SHA-256 | 쓰임 |
|---|---|---|---|
| 디버그 (`app/debug.keystore`, 저장소에 고정) | `16:DD:90:2E:03:2A:B6:41:24:F6:6A:8B:90:ED:05:05:8C:53:8C:4F` | `09:97:EB:33:07:27:5D:45:B4:05:33:81:CA:61:41:1A:F0:BA:CF:36:41:87:D2:CA:0C:4D:F1:C1:A5:C7:88:B3` | CI 테스트 APK, 로컬 디버그 |
| Play 앱 서명 키 | Play Console → 테스트 및 출시 → 앱 무결성 → 앱 서명에서 확인 | 〃 | 스토어에서 받은 앱 |
| 업로드 키 | 출시 빌드용 키를 만들 때 기록 | 〃 | CI 출시 빌드 |

디버그 키는 표준 디버그 비밀번호(android)를 쓰는 테스트 전용 키라 저장소에 둔다. 출시 키는 절대 커밋하지 않는다.
