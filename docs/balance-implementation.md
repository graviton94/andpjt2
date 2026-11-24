# 알림 청소부 앱 밸런스 구현

앱과 검증 러너는 같은 `GameEngine`, `EconomyRules`, 카탈로그를 컴파일한다. Android는 테스트 거래를 디버그 빌드에서 제공한다. 투입 가격은 원화 결제 의향을 추정하기 위한 가격표다.

## 운영 규칙

- 일반 P는 기본 점수의 자릿수와 연쇄로 계산한다. 앱별 유효 파동을 합하고 앱 강화 배율을 곱하며, 앱 고유 가산을 한 번 더한 뒤 권역·카테고리·이벤트 배율을 곱한다. 앱별 내림 후 합산한다. 높은 레벨의 과부하 파동은 등비 가중 합으로 집계한다.
- 첫 구매 앱은 밀린메일 475 P로 조정했다. 비트서랍 1,800 P, 페이한칸 6,000 P, 첫 강화 600 P를 적용한다. 450 P 최초 후보의 실패와 가격 비교는 결과 폴더의 opening CSV에서 확인할 수 있다. 강화 배율과 단계 가격은 앱과 카탈로그가 같은 함수를 사용한다.
- 영구 마스터리·스타터 팩·테마·패스를 직접 구매한다. 소모품은 유료재화 100(증폭기) 또는 200(마스터리 체험)을 차감한다. 100/500원 및 600/2,900원 충전을 제공한다. 출석 이정표의 2,000원/4,000원은 100 충전 단가를 기준으로 한 명목값이다. 묶음 비례 단가는 각각 약 1,933원/3,867원이며, 실제 이 수량의 최저 충전 지출은 잔액이 없을 때 2,000원/3,900원이다.
- 부스트는 수동으로 준비한다. 마스터리를 산 앱을 직접 장착할 수 있고, 체험은 선택 앱의 다음 유효 기여 3회를 사용한다. 보유자는 중복 체험권을 증폭기 2개로 교환할 수 있다. 초반의 기본 점수와 P는 장비나 보너스에 영향받지 않는다.
- 다음 달 테스트 갱신은 남은 기간 뒤로 한 달을 연장한다. 획득한 패스 테마의 점수 혜택은 보존하고 특수 기물·도전 신규입장·프리셋 권한은 활성 기간에 제공한다. 전용 도전은 고정 보드 한 번의 선택으로 끝나고 일반 경제와 별도 기록에 저장한다. 트랙 5/15/30회는 이 버전의 단일 보상 트랙이다.
- 저장 v2는 기존 일반 진행과 P를 이전하며, 이전 점수 공식의 최고점은 레거시 기록으로 보관한다. 테스트 거래와 재고는 로컬에 원자적으로 저장한다. 검증용 복원은 로컬 저장 재시작과 이미 확정된 테스트 거래 재전달을 대상으로 한다.

## 재현

```bash
./scripts/balance-backtest.sh /workspace/app-balance-report/raw 201 30
./scripts/balance-backtest.sh --commerce /workspace/app-balance-report/commerce 201 30
./scripts/balance-backtest.sh --comparison /workspace/app-balance-report/comparison 201 30
./scripts/balance-backtest.sh --mastery-audit /workspace/app-balance-report/mastery-audit 201 30
python3 scripts/balance-report.py /workspace/app-balance-report
```

`--opening 161 30`은 첫 구매 가격 450, 475, 500, 525, 550 P를 조정 시드에서 비교한다. `--opening 201 30`은 별도 검증 시드다. 원본 레벨·시간 열에서 빈 값은 관측 기간 중 미도달이다. 소모품 정책은 재고가 있으면 다음 과부하에 쓰며, 수동 복귀·대기 환생의 차이는 각각 정규 일정 및 월간 일정에서 따로 계산한다. SKU 효율 비교는 현재 가격표, 정해진 구매·장비·조작 정책과 관측 기간에 대한 값이다.

```bash
source /workspace/.andpjt2-android/activate.sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.graviton94.notificationcleaner.playtest.test/com.graviton94.notificationcleaner.BalanceInstrumentation
python3 scripts/android-smoke.py app/build/outputs/apk/debug/app-debug.apk
```

Android 장치 테스트는 고립된 저장소에서 충전·재고·장착·연쇄·복원·환생을 검사한다. 시뮬레이터에서 통과한 값으로 실제 구매율·광고 수락률·순매출을 단정할 수 없으며, 실제 판매·광고 공급자와 서버 원장은 출시용 작업이다.

개별 실행은 `./scripts/balance-backtest.sh --replay /tmp/cleaner-replay 201 calendar auto pass`로 재생한다.
앱별 마스터리 감사에서는 동일 스와이프를 유지하면서 해당 앱 첫 보유 뒤 보너스만 바꾼다. 비교 모드의 P 광고와 추가 P 10%는 판매 메뉴 없이 보고서에서만 실행한다.
