# 유료 전용 EX 꾸미기와 P 수입 설계안

첫 EX 상품은 **프리즘 EX 팩 · ₩5,500 · 1회 구매**를 제안한다. 프리즘 테마, 스와이프 궤적, 연쇄 효과, 프로필 프레임과 함께 일반 플레이 스와이프 P **+3%**를 구매 즉시 계정에 부여한다.

| 일반 플레이 내 보유 상태 | 스와이프 P 가산 |
|---|---:|
| 기본 꾸미기 | 0% |
| EX 꾸미기 한 개 이상 보유 | +3% |

EX 팩은 현금 직판 상품으로 판매한다. EX 상품을 추가하는 경우 각 상품의 외형은 고유하고 수입 혜택은 계정당 한 번만 적용한다. 결제 확인창에 상품 가격, 현재 수입 가산율, 구매 후 가산율을 표시한다. EX 수입 혜택은 소유 기준으로 적용하므로 기본 외형을 장착해도 유지된다.

## P와 기록이 바뀌는 지점

스와이프의 기존 정산을 마친 뒤 레벨 상한 적용 직전에 `floor(기본 P × 103 / 100)`을 계산한다. 일반 스와이프와 자동 정리에 같은 규칙을 적용한다. 동일한 스와이프에서 기존 정산이 1,000 P이고 레벨 상한에 여유가 있다면 1,030 P를 지급한다.

지급 P는 지갑, 회차 P, 개인 최고 스와이프 기록, 최근 수입률에 반영한다. 복귀 정산과 시간 도약은 기존 최근 수입률을 통해 간접 반영된다. 고정 시드 일일 도전에서는 기존과 같이 계정 혜택을 제외한다.

## 승인 후 구현 범위

- EX는 비소모성 상품으로 분류한다. 계정에 SKU별 보유권을 보관하고 공장 초기화와 GB 동기화 이후에도 유지한다. 이전 저장은 보유권이 비어 있는 상태에서 연다.
- 현재 앱에서 검증할 구매 경로는 디버그 거래다. 향후 Play 연결 시 검증된 구매 영수증으로 지급하고, 중복 수신은 같은 보유권을 반환한다. 복원과 환불 처리 후 보유 중인 EX가 하나 이상이면 +3%를 적용한다.
- 상점과 기록 화면에 넘길 API에는 보유 SKU, 현재 가산율과 상품 구매 후 가산율을 추가한다. 스와이프 결과에 덧붙일 금액은 레벨 상한 적용 후 실제로 가산된 P다.

## Lv124 결과

EX 보유는 기존 코어 사본에서 첫날부터 스와이프 P +3%를 적용한 실험과 같다. 기존 검증 CSV를 재판정했다. 각 조건은 84일, 하루 4회 × 15분, 시드 1–10이다. 기본 프로필과 보상 사용 정책은 같고, EX 구매 비용 ₩5,500을 별도로 가정한다. 무광고·광고의 EX 보유 조건은 각각 EX 단독 구매자와 EX를 구매한 광고 유저를 뜻한다.

| 플레이 정책 | 기본 중앙값 · 목표 구간 | EX 보유 중앙값 · 목표 구간 | 같은 시드에서 빨라짐 / 같음 / 늦어짐 |
|---|---:|---:|---:|
| 무광고 | 75.42일 · 9/10 | 76.58일 · 10/10 | 5 / 0 / 5 |
| 광고 하루 5회 | 56.09일 · 10/10 | 58.08일 · 9/10 | 5 / 0 / 5 |
| 월간 패스 | 43.58일 · 10/10 | 43.08일 · 10/10 | 6 / 2 / 2 |
| 터보 | 27.17일 · 10/10 | 26.76일 · 10/10 | 8 / 0 / 2 |
| 울트라 | 16.17일 · 8/10 | 16.25일 · 8/10 | 7 / 0 / 3 |
| 월간 패스 · 화면에 있는 보상만 | 47.00일 · 8/10 | 46.17일 · 9/10 | 6 / 0 / 4 |

두 조건에서 다섯 프로필 모두 중앙값과 8/10 기준을 충족했다. P 증가가 앱 매입, 덱, 환생 시점을 바꾸므로 상품 설명에는 P 가산율을 표시한다. [기본 및 +3% 원자료](offline-pass-review.md)

무료 +1%를 Lv10에서 열고 EX에 +2%를 부여하는 배분도 월간 패스의 현재 화면 기준으로 비교했다. EX 미보유는 46.50일·7/10, EX 보유는 46.00일·8/10이었다. 미보유 조건이 기준에 미달해 +3%를 EX에 배정했다. [배분안 비교 원자료](cosmetic-ex-split-visible-seeds.csv)

## 재현

```sh
./gradlew -p tools/economy-sim run --args="balance-report /tmp/cosmetic-base"
python3 tools/economy-sim/cosmetic-probe.py /tmp/cosmetic-ex --bonus-permille 30
./gradlew -p tools/economy-sim run --args="balance-report /tmp/cosmetic-base-live-pass Pass available-rewards"
python3 tools/economy-sim/cosmetic-probe.py /tmp/cosmetic-ex-live-pass --bonus-permille 30 --profiles Pass --available-rewards
python3 tools/economy-sim/cosmetic-probe.py /tmp/cosmetic-split-free --ex-plan free --profiles Pass --available-rewards
python3 tools/economy-sim/cosmetic-probe.py /tmp/cosmetic-split-ex --ex-plan ex --profiles Pass --available-rewards
```
