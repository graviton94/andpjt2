# 알림 청소부

스마트폰 알림창을 한 손으로 무너뜨리는 방치형 연쇄 퍼즐(Android). 알림 한 줄을 밀어 같은 앱끼리 터뜨리고, 고른 덱과 쌓인 배율이 같은 스와이프를 수십 자리 숫자로 바꾼다. 막히면 기기를 공장 초기화해 더 깊이 간다.

## 시작은 바이블에서

**[docs/bible](docs/bible/README.md)** 가 프로젝트의 단일 진입점입니다. 비전, 마스터 개발 플랜, 개발 현황, 코어 루프, 경제, 콘텐츠 참조표(코드에서 자동 생성), 화면, 아키텍처, 디렉토리 구조, 사례조사 적용과 추가 조사 발주서, 결정 기록, 용어집이 있습니다.

현재 **2단계(경제 + 콘텐츠)** 진행 중입니다. 본편 판정 엔진은 순수 Kotlin 모듈 [`core/`](core), 경제 시뮬레이터는 [`tools/economy-sim`](tools/economy-sim)입니다.

```sh
./gradlew -p core test                                    # 규칙 테스트 (CI)
./gradlew -p core run --args="content" > docs/bible/05-content-reference.md   # 참조표 재생성
./gradlew -p tools/economy-sim run --args="daily 56 4"    # 8주 일상 플레이 시뮬레이션
```

`app/`은 손맛과 첫 연쇄를 검증했던 **시연 앱 《알림 청소부 FX》**이며 자체 엔진을 씁니다. 3단계 그레이박스에서 `core` 기반으로 교체합니다. 시연 시절 기획서는 기록용으로 `docs/notification-cleaner-*.md`에 남아 있습니다.

## 시연 앱 빌드와 첫 플레이

JDK 17, Android SDK Platform 36, Build Tools 35.0.0을 사용합니다. Android 8.0 이상에서 기존 테스트 앱과 함께 설치할 수 있습니다. 앱 이름은 《알림 청소부 FX》입니다.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

생성 파일은 `app/build/outputs/apk/debug/app-debug.apk`입니다. [Android test build](https://github.com/graviton94/andpjt2/actions/workflows/android.yml)에는 APK와 에뮬레이터 스모크 결과가 남습니다.

1. 첫 화면의 **문앞왔**을 옆으로 밀면 같은 앱 세 쌍이 충돌해 과부하가 이어집니다. 시연판 기준 **7연쇄 · 14,967개 처리 · 117,645 P**입니다.
2. **자동 꺼짐**을 눌러 자동 정리를 켜거나, **앱 상점**에서 새 앱을 사고 강화합니다. **최고점 기록**에서 연쇄와 권역·카테고리 배율을 확인합니다.
3. **모의 재난문자**에서는 60초 동안 탭해 배율을 올립니다. 시연판의 두 배 보상은 체험 버튼이고, 최고점은 기기에 저장됩니다.

네트워크·기기 연결·손전등은 앱 안에서 재현합니다. 밝기 슬라이더는 앱 화면 밝기를 바꿉니다. 설정에서 **움직임 줄이기**, 효과음과 진동을 각각 조절합니다.

## 레퍼런스와 이전 기획

- [UI UX Pro Max](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill)로 Compose 화면 정보 계층과 접근성을 검토합니다. [pixelfx](https://github.com/patch-gg/pixelfx) 타임라인을 참고하고 시연 앱의 Hit spark·Critical hit 스프라이트를 활용합니다.
- [Kenney Game Icons](https://kenney.nl/assets/game-icons)는 CC0입니다. 시연판 스프라이트와 아이콘의 출처는 [APK 에셋 라이선스](app/src/main/assets/licenses)에 있습니다.
- [UI 테마 시안 3종](docs/design/ui-theme-candidates.md) · 본편 UI의 정본 후보
- [초기 마스터플랜](docs/notification-cleaner-master-plan.md) · [파쇄 콘셉트 보드](docs/notification-cleaner-concept-board.png) · [자동 연쇄와 배율 콘셉트 보드](docs/notification-cleaner-score-concept-board.png)
