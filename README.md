# 알림 청소부

휴대폰의 알림 센터에서 한 줄을 밀어 같은 앱을 연결하고, 연쇄와 배율을 키우는 Android 증분 게임의 테스트 빌드입니다.

## 테스트 빌드

JDK 17, Android SDK Platform 36, Build Tools 35.0.0을 사용합니다. Android 8.0 이상에서 디버그 APK를 설치할 수 있습니다.

```sh
./gradlew testDebugUnitTest assembleDebug
```

생성 파일은 `app/build/outputs/apk/debug/app-debug.apk`입니다. GitHub Actions의 Android test build도 같은 명령을 실행하고 디버그 APK를 남깁니다.

## 첫 플레이

1. 처음 화면의 **문앞왔** 행을 옆으로 충분히 밀면 메시지와 메일이 두 번 연달아 폭발합니다.
2. 하단의 **자동 꺼짐**을 누르면 스와이프 충전 주기에 맞춰 가능한 연쇄를 자동으로 고릅니다.
3. 상점에서 새 앱을 구하거나 보유 앱을 강화하면 알림 종류와 보상이 늘어납니다. **정리 기록**에서 최고점과 권역·카테고리 배율을 볼 수 있습니다.
4. 게임 속 **모의 재난문자**가 발령되면 60초 동안 탭으로 배율을 올릴 수 있습니다.

빠른 설정의 네트워크와 기기 목록, 비행기 모드 및 손전등은 게임 안의 설정입니다. 밝기 슬라이더는 이 앱의 화면 밝기를 바꾸고, 알림 관리에서는 미리보기·소리·진동을 조절할 수 있습니다. 보상 버튼은 광고 정산을 확인하는 체험이고, 기록은 현재 기기에 저장합니다.

## 기획 자료

- [마스터플랜](docs/notification-cleaner-master-plan.md)
- [파쇄 연출 컨셉보드](docs/notification-cleaner-concept-board.png)
- [자동 연쇄와 배율 성장 컨셉보드](docs/notification-cleaner-score-concept-board.png)
