# 패스 오프라인 보너스와 꾸미기 P 수입 검증

84일 동안 프로필별 시드 1–10으로 하루 4회 × 15분을 실행했다. 첫 Lv124에서 해당 시드를 종료한다. 중앙값과 8/10 이상이 기존 목표 구간에 들어야 통과한다. 미도달은 가장 느린 값으로 처리하고 분모에 포함한다.

| 프로필 | Lv124 목표 | ae43417 | 패스 +70% 적용 | 꾸미기 +3% 추가 |
|---|---:|---:|---:|---:|
| 무과금 | 66–84일 | 75.42일 · 9/10 · 통과 | 75.42일 · 9/10 · 통과 | 76.58일 · 10/10 · 통과 |
| 광고 하루 5회 | 49–63일 | 56.09일 · 10/10 · 통과 | 56.09일 · 10/10 · 통과 | 58.08일 · 9/10 · 통과 |
| 월간 패스 | 35–49일 | 48.17일 · 8/10 · 통과 | 43.58일 · 10/10 · 통과 | 43.08일 · 10/10 · 통과 |
| 터보 | 24–32일 | 28.26일 · 10/10 · 통과 | 27.17일 · 10/10 · 통과 | 26.76일 · 10/10 · 통과 |
| 울트라 | 11–17일 | 16.42일 · 9/10 · 통과 | 16.17일 · 8/10 · 통과 | 16.25일 · 8/10 · 통과 |

월간 패스는 기존 8/10에서 10/10으로 바뀌었고 중앙값은 43.58일이다. 현재 화면에 노출된 지면만 사용한 월간 패스는 중앙값 47.00일에 8/10으로 통과선에 걸쳤다. 위 표는 기존 비교 정책의 오프라인 ×2 지면을 포함한다.

## 중첩과 정산

광고 시청일·다음 UTC 날짜 또는 활성 패스 중 하나 이상 유효하면 오프라인 정산에 1.7배를 적용한다. 터보와 울트라도 공통 패스 혜택을 받는다. 울트라에서 1.7과 1.5를 곱한 총배율은 2.55다. 최종 지급액에는 기존 레벨 상한을 적용한다.

단순 합산으로 총 2.2배를 지급한 울트라는 중앙값 16.50일에 6/10으로 미달했다. 곱연산을 적용한 울트라는 중앙값 16.17일에 8/10으로 통과했다.

## 무과금 미도달 시드

무과금 시드 4는 79.00일에 처음 Lv123에 도달했다. 이후 81.51일 Lv123에서 동기화하고 83.01일 Lv122에서 초기화해 회차 P가 두 차례 재시작됐으며, 84일 종료 시 Lv123까지 회복했다. [미도달 추적 CSV](offline-pass-stalls.csv)

## 꾸미기 보너스

동일한 엔진 사본에 스와이프 P 상한 적용 전 +3%를 추가하고 첫날부터 가산율을 유지했다. [꾸미기 및 외형 판매안](cosmetics-proposal.md)

현재 화면의 보상 지면만 사용한 월간 패스에 +3%를 더하면 중앙값은 46.17일이며, 9/10이 목표 구간에 들었다. [해당 시드별 CSV](cosmetic-probe-visible-pass-seeds.csv)

+3% 실험에서 다섯 프로필 모두 중앙값과 80% 기준을 충족했다.

## 재현

`./gradlew -p tools/economy-sim run --args="balance-report /tmp/offline-pass"`

`./gradlew -p tools/economy-sim run --args="balance-report /tmp/offline-pass-visible Pass available-rewards"`

`python3 tools/economy-sim/cosmetic-probe.py /tmp/cosmetic-three-percent --bonus-permille 30`

`python3 tools/economy-sim/cosmetic-probe.py /tmp/cosmetic-visible-pass --bonus-permille 30 --profiles Pass --available-rewards`

[패스 시드별 CSV](offline-pass-seeds.csv) · [패스 판정표](offline-pass-gates.csv) · [화면 노출 지면 패스 CSV](offline-pass-visible-seeds.csv) · [꾸미기 시드별 CSV](cosmetic-probe-seeds.csv) · [꾸미기 판정표](cosmetic-probe-gates.csv)
