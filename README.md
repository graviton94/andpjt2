# 알림 청소부

휴대폰의 알림 센터에서 한 줄을 밀어 같은 앱을 연결하고, 연쇄와 배율을 키우는 Android 증분 게임의 테스트 빌드입니다.

## 테스트 빌드

JDK 17, Android SDK Platform 36, Build Tools 35.0.0을 사용합니다. Android 8.0 이상에서 디버그 APK를 설치할 수 있습니다.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

생성 파일은 `app/build/outputs/apk/debug/app-debug.apk`입니다. 디버그 APK는 별도 패키지로 설치되므로 기존 테스트 앱과 함께 둘 수 있습니다. GitHub Actions에 APK와 첫 스와이프 화면 녹화가 남습니다.

## 첫 플레이

1. 처음 화면의 **문앞왔**을 옆으로 밀면 같은 앱 세 쌍이 충돌합니다. 그 충격으로 과부하가 발동해 7연쇄, 14,967개 처리, 117,645 P까지 올라갑니다.
2. 하단의 **자동 꺼짐**을 누르면 스와이프 충전 주기에 맞춰 가능한 연쇄를 자동으로 고릅니다.
3. 상점에서 새 앱을 구하거나 보유 앱을 강화하면 알림 종류와 보상이 늘어납니다. **정리 기록**에서 최고점과 권역·카테고리 배율을 볼 수 있습니다.
4. 게임 속 **모의 재난문자**가 발령되면 60초 동안 탭으로 배율을 올릴 수 있습니다.

빠른 설정의 네트워크와 기기 목록, 비행기 모드 및 손전등은 게임 안의 설정입니다. 밝기 슬라이더는 이 앱의 화면 밝기를 바꾸고, 알림 관리에서는 미리보기·소리·진동을 조절할 수 있습니다. 설정의 **움직임 줄이기**는 흔들림, 섬광, 파티클을 끕니다. 보상 버튼은 광고 정산을 확인하는 체험이고, 기록은 현재 기기에 저장합니다.

## 연쇄와 에셋

알림이 붙으면 묶음 수량이 합쳐지고, 임계치에 닿으면 폭발합니다. 같은 앱이 든 나머지 대기열은 과부하에서 파동마다 곱으로 커집니다. 후반부에는 한 번의 연출에 여러 파동을 묶어서 표시하며 처리량과 점수는 전부 반영합니다. 점수와 묶음 수량은 큰 정수로 저장합니다.

스와이프의 발사, 카드 충돌, 과부하, 알림창 붕괴를 개별 효과음과 햅틱으로 구분합니다. 패널의 정보 위계와 조작 상태는 [UI UX Pro Max](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill)의 Compose 가이드를 참고했습니다. [pixelfx](https://github.com/patch-gg/pixelfx)의 Hit spark와 Critical hit를 스프라이트로 탑재했습니다. 가상 앱과 게임 조작 아이콘은 [Kenney Game Icons](https://kenney.nl/assets/game-icons)의 CC0 에셋을 사용합니다. 원문 라이선스는 [APK 에셋](app/src/main/assets/licenses)에 포함됩니다.

## 초기 기획 자료

- [마스터플랜](docs/notification-cleaner-master-plan.md)
- [파쇄 연출 컨셉보드](docs/notification-cleaner-concept-board.png)
- [자동 연쇄와 배율 성장 컨셉보드](docs/notification-cleaner-score-concept-board.png)
