# 실행 검증

- Gradle 단위 테스트 12개 통과, 오류·건너뜀 0개.
- Debug lint 오류 0개, 경고 3개. 디버그 앱과 Android 계측 APK 빌드 성공. 앱 APK v2 서명 검증 통과.
- 동일 JVM 엔진 실험의 최종 40개 기준 통과. 시드 201의 calendar / auto / pass를 별도 실행해 기존 CSV의 모든 원래 열이 일치함을 확인.
- Android API 30 소프트웨어 환경에서는 두 APK의 설치가 성공했으나 계측 실행이 시간 초과됐다. Activity 시작도 timeout을 반환했다. Android 이벤트에는 System UI·전화·미디어 서비스 ANR 및 system_server watchdog 정지가 있었다. Android UI 구매와 저장 재실행의 현장 통과 판정은 보류한다.
- 테스트 거래는 디버그용 로컬 지급과 이미 받은 영수증 재전달을 사용한다. 실제 Play Billing, 광고 SDK 콜백 및 재설치 후 서버 복원은 이번 검증 범위와 구분한다.
