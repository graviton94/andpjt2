# B안 · 새 강화 구간과 환생 대기 재검증

SoftReplay 기준선 이후의 동기화 계승·EX·가격 검증은 [SYNC-FOLLOWUP-REPORT.md](SYNC-FOLLOWUP-REPORT.md)에 있다.

**날짜별 그래프 원본**은 규칙 후보, 접속 유형, 프로필, 보유 수량별로 누적 P·지갑·파일·GB, 최고·현재 Lv, 회차, 실제로 추가 인정된 오프라인 분을 담는다. 6프로필은 무광고·광고·월간 패스·화면 보상만 쓰는 월간 패스·터보·울트라다. P·파일·GB의 log10에서 0은 0개 또는 1개를 뜻하므로 개별 시드의 지급을 확인할 때는 지급 CSV도 함께 본다. [daily-growth.csv](daily-growth.csv) · [원자료 매니페스트](b-calibration-data/manifest.json)

## 결론

**새 강화 구간에만 가격을 올리는 방식은 Merit에서 P 손실 기준을 통과했다.** 기존에 도달한 단계까지 기본 가격을 그대로 쓰고 그 이후 24단계에만 보정을 올렸다. 6프로필 × 30시드 × 168일 미보유 짝에서 최고 손실은 울트라 시드 22의 58일차 **0.894자리**였다.

**후속 검증에는 SoftReplay를 권한다.** 반복 동기화와 완만한 새 구간 가격을 조합해 모든 프로필의 GB 간격 중앙값이 3세션이 됐고, 180/180시드가 마지막 28일 성장 기준을 통과했다. 같은 시드의 P 손실은 174/180개가 기준을 지켰다. 핵심 미달은 동기화 직후 재도달 가속, 초기화 구매 수, 가벼운 플레이의 B 체감이다. 이식 승인 판단은 아래 표에서 각 지표를 따로 읽어야 한다.

## 1. 무엇을 바꿨는가

B의 유료 9SKU·세트 완성·할인·시간 혜택을 유지했다. A는 이번 실행에서 제외했다. 운영 기준은 `d14f4c2`이며, 아래 수정은 실제 코어를 임시 워크트리에 적용하는 재실행 패치다. Lv124는 그래프와 재도달 표에서만 읽는다. [cadence-core.patch](../../tools/economy-sim/growth-loop/cadence-core.patch) · [CadenceProbe.kt](../../tools/economy-sim/growth-loop/CadenceProbe.kt)

| 항목 | 실험한 식과 조건 |
|---|---|
| 유료 EX B | n = 보유 SKU 수, s = 완성 세트 수, f = 풀세트 여부(0 또는 1). P 배율 1.07ⁿ×1.13ˢ×1.15ᶠ, 반복 강화 가격 계수 0.975ⁿ×0.965ˢ×0.97ᶠ, 오프라인 상한 +8n+25s분. 한 세트는 P ×1.384·가격 ×0.894·+49분, 풀세트는 P ×3.051·가격 ×0.694·+147분이다. 파일·GB의 지급 원금에 EX 배율을 직접 곱하지 않는다. |
| 기본 앱 가격 | 해당 앱의 기존 P 가격 C₀(r). 앱 가치 ×1.20과 기본 가격 ×1.30 단계 기울기를 유지한다. |
| 새 강화 구간 | F = max(25, 해당 앱의 유효 환생 최고 강화 단계), x = clamp((r−F)/24, 0, 1), w = 3x²−2x³. 견적은 ⌊C₀(r)×[1+(M−1)w]⌋이다. M = max(1, √(계승한 Lv1 메모의 기준 가치/3.02))이고 천분율 정수로 저장한다. 기존 앱 패시브 할인과 B안 곱할인을 적용한다. |
| 새 구간의 완만한 보정 | SoftReplay는 G = ⌊1,000×M⌋, d = max(1, G의 십진 자릿수−3)으로 계산한다. 위 견적식의 M 자리에 min(G, 1,000+5d)/1,000을 넣는다. 단계 기본 가격 C₀는 계속 지수적으로 증가한다. |
| 파일 기준 권리 | X = 회차 P ÷ Lv25의 기준 P, T = 10⁸. X ≤ T일 때 ⌊10×X^0.40⌋, 이후 ⌊10×T^0.40×(X/T)^0.24⌋. 기존 꼬리 0.20·대조 0.22에서 바꿨다. 실제 원금은 이전에 수령한 기록의 증가분이다. |
| GB 기준 권리 | Y = (이번 동기화 주기의 누적 파일 + 이번 판의 예상 파일) ÷ Lv60 P에 해당하는 기본 파일. ⌊10×Y^0.60⌋에서 이미 수령한 기록 원금을 뺀 양을 지급에 쓴다. 기준 지수 0.50, 중간 대조는 0.55였다. |
| GB 반복 가격 | Q(n) = ⌈시작가×[1+0.8×(기존 기울기−1)]ⁿ⌉. 압축 ×2.30→×2.04, 동기화 가치 ×2.60→×2.28, 오프라인 시간 ×2.00→×1.80이다. |
| 플레이 후 지급 바닥 | 실제 플레이어 스와이프 간격에 회당 최대 12초를 인정한다. Lv25·12분을 충족한 초기화는 미보유의 가장 싼 반복 강화 3개를 살 만큼, Lv60·25분을 충족한 동기화는 2개를 살 만큼 지급 바닥을 둔다. 지급액은 기록 보상과 바닥 중 큰 쪽이다. 원금 기록과 바닥 지급을 분리하며 스와이프 시간을 납입과 함께 소비한다. 선지급은 사용하지 않았다. |
| 반복 동기화 대조 | 첫 동기화는 Lv60 유지. 한 번 수령한 계정에는 Lv25와 누적 실플레이 25분을 충족할 때 반복 지급 권리를 연다. 새 기록 전용 GB 곡선은 계속 Lv60 이상에서 계산한다. 덱 대조도 같은 객관 가치로 시험했다. |

가격 계산은 BigInteger이며 엔진의 표기·가격·구매 가능 여부가 같은 견적을 읽는다. 지수도 해당 유리수의 정수 제곱·정수근으로 계산했다. 유효 지급이 없던 종료는 가격 구간 기록을 올리지 않는다. 새 필드는 저장 기본값을 가지고, 새 회차의 가격 보정은 저장 복원 뒤에도 같았다.

## 2. 168일 본 판정

하루 4회 × 15분, 6프로필 × 30시드, 가격 인덱스 유무의 **360경로**다. 첫 8스와이프에는 엔진 충전 시간을 쓰고, 이후 이 플레이어는 스와이프 사이 최소 12초를 뒀다. 실험 플레이어는 세션 끝에 새 기록과 살 수 있는 강화가 있으면 초기화하고, 그 외에는 활성 45분 시점까지 쌓는다.

| 지표 | 기준 | Merit 본 결과 |
|---|---|---|
| 같은 시드의 P 손실 | 1자리 이내 | **통과**. 180쌍 × 168일에서 초과 0. 최고 0.894자리. |
| P 구매 대기 | 초반 3초~1분, 후반 1~10분 | 인덱스 적용의 충족률은 초반 **1.42~3.32%**, 후반 **1.49~5.98%**. 10분 초과는 각 프로필 **0.015% 미만**이었다. |
| 초기화에 따른 반복 구매 수 | 3~8개 | **27,375/37,163** 지급이 범위 안. 모든 프로필 중앙값은 3개. |
| 다음 초기화 활성 시간 | 15~45분 | **34,428/36,983** 간격이 범위 안. 모든 프로필 중앙값은 45분. |
| 동기화에 따른 반복 구매 수 | 2~5개 | **679/879** 지급이 범위 안. 모든 프로필 중앙값은 2개. |
| 다음 동기화 | 2~4세션 | **0/699**. 프로필별 중앙값 **58~112세션**. |
| 동일 Lv 재도달 | 중앙값 ≤0.80, 앞선 회차 ≥70% | Lv12와 Lv25의 프로필별 중앙값은 모두 **1.00**이었다. Lv12의 앞선 비율 **11.1~32.1%**, Lv25 **29.5~46.0%**. Lv60·124에는 서로 이웃한 두 회차에서 모두 도달한 짝이 없었다. |
| 마지막 28일의 누적 P | 시드별 +0.13자리/일 이상과 정체일 0 | **0/180**. 프로필별 중앙값 **+0.00345~+0.00915자리/일**, 정체일 **1,018일**. |

강화 대기는 방금 살펴본 활성 덱 앱의 다음 가격을 실제 최근 P/분으로 나눴다. 초반은 회차의 첫 10활성 분이다. 인덱스에 노출될 수 있는 새 구간의 견적 표본은 6프로필 합계 **6,855개**였다. 목표 범위 밖의 견적은 대부분 목표 하한보다 빨리 살 수 있었다. 계승 수입과 비교하면 기존 단계의 기본 가격은 충분히 낮았다. [기본 가격과 대기 충족률](b-calibration-tables/Merit/Loop/waits.csv) · [모든 시드의 최대 P 손실](b-calibration-tables/Merit/Loop/price-loss-all-days.csv)

환생 구매 수는 **그 지급액만** 남긴 상태에서 당시 해금된 싼 반복 강화를 차례대로 실제 구매하여 센다. 12개에서 탐색을 멈추고 12 이상으로 표시한다. 상점 자체에 구매 한도는 없다. 지급 간격은 두 번 유효 지급 사이에서만 계산했고, 최초 지급은 구매 수에는 포함했다. 재도달은 인접 회차가 둘 다 같은 Lv를 실제로 밟은 경우만 분모에 넣었다. 동률은 앞선 것으로 세지 않았고, 도달하지 않은 단계의 표본 부재도 남긴다. [지급과 간격](b-calibration-tables/Merit/Loop/payouts.csv) · [Lv별 재도달](b-calibration-tables/Merit/Loop/acceleration.csv) · [배후 환생 종류별 재도달](b-calibration-tables/Merit/Loop/acceleration-transitions.csv) · [마지막 28일](b-calibration-tables/Merit/Loop/tail.csv)

### 후속 권리·덱 대조

덱에 새로 산 고가치 앱을 넣은 84일의 Focus 대조에서 동기화 **0/30간격**이 2~4세션 범위에 들었다. 같은 플레이어로 첫 동기화 이후에만 반복 권리를 연 Replay 예비 대조는 **556/583**이었다. 이 차이는 반복 권리 조건이 간격 병목의 핵심이었다는 근거다. [덱만 바꾼 Focus 대조](b-calibration-tables/FocusPilot/Loop/payouts.csv) · [같은 시드 반복 권리 대조](b-calibration-tables/ReplayPilot/Loop/payouts.csv)

| 6프로필 × 30시드 × 168일 | 본 대조 Merit | 반복 권리 Replay | 완만한 가격 SoftReplay |
|---|---:|---:|---:|
| 초기화 3~8개 · 활성 15~45분 | 27,375/37,163 · 34,428/36,983 | 10,360/33,821 · 20,799/33,641 | **10,261/33,776 · 20,697/33,596** |
| 동기화 2~5개 · 2~4세션 | 679/879 · 0/699 | 29,270/29,450 · 29,001/29,270 | **29,375/29,555 · 29,080/29,375** |
| 동기화 간격의 프로필별 중앙값 | 58~112세션 | **모두 3세션** | **모두 3세션** |
| 마지막 28일 +0.13자리/일과 정체일 0 | 0/180시드 | **180/180시드**, 중앙값 +0.500~+0.540자리/일 | **180/180시드**, 중앙값 +0.463~+0.531자리/일 |
| 어느 날이나 인덱스로 인한 P 손실 1자리 이내 | **180/180시드**, 최대 0.894 | 2/180시드, 최대 4.743자리 | **174/180시드**, 최대 1.596자리 |
| 재도달 목표 | 0/24 프로필·Lv | 0/24. Lv60 표본 13,817쌍, Lv124 인접 표본 0 | **0/24**. Lv60 표본 14,013쌍, Lv124 인접 표본 0 |

Replay에서는 마지막 관측일 이후로도 진행해야 하는 **미완료 간격**을 따로 셌다. 종료 시점에 동기화 대기가 이미 4세션을 넘은 시드는 0개다. Merit는 164개다. 반면 Replay의 초기화 구매 수와 간격은 더 자주 범위를 벗어났다. 두 환생 층의 주기가 서로 영향을 준다는 증거다. [Replay 지급](b-calibration-tables/Replay/Loop/payouts.csv) · [Replay 성장](b-calibration-tables/Replay/Loop/tail.csv) · [Replay P 손실](b-calibration-tables/Replay/Loop/price-loss-all-days.csv)

계승 수입의 후속 대조에서는 이전 동기화에서 지급 원금으로 확정한 파일 기록의 최댓값을 저장하고, 그 기록의 기존 수입 배율에서 **4제곱근**을 다음 회차에 이월했다. 같은 기록을 반복 지급해도 이월 원금은 증가하지 않는다. 3시드·84일 Inherit에서 2~4세션은 **612/617**이었다. 마지막 28일의 성장 기준은 울트라 3/3이 통과했지만 나머지 15시드는 미달했다. 누적 P 손실은 5시드에서 1자리를 넘었고 최대 2.786자리였다. 계승 배율을 전체 프로필의 해결책으로 채택할 수 없다. [계승 수입 판정](b-calibration-tables/InheritPilot/Loop/tail.csv) · [가격 손실](b-calibration-tables/InheritPilot/Loop/price-loss-all-days.csv)

SoftReplay의 초반 대기 충족률은 **2.25~3.98%**, 후반은 **2.17~7.10%**였다. 10분 초과는 각 프로필 **0.016% 미만**이다. 새 구간 노출 견적은 30,451개다. P 손실 1자리 초과는 **6시드의 9일**에 집중됐고, 최댓값은 월간 패스 시드 10의 151일차 **1.596자리**였다. 가격을 조금 움직여도 그 시점의 구매와 다음 정산이 달라질 수 있으므로, 174/180은 전체 통과로 처리할 수 없다. [SoftReplay 대기](b-calibration-tables/SoftReplay/Loop/waits.csv) · [SoftReplay P 손실](b-calibration-tables/SoftReplay/Loop/price-loss-all-days.csv) · [SoftReplay 지급·간격](b-calibration-tables/SoftReplay/Loop/payouts.csv) · [SoftReplay 마지막 28일](b-calibration-tables/SoftReplay/Loop/tail.csv)

월간 패스 시드 10의 151일차는 양쪽 모두 세션 602·604에서 동기화했고 603에서 초기화했다. 마지막 동기화 직전 레벨은 기본 가격 Lv117, 보정 Lv114였다. 152일차에는 둘 다 Lv119, 153일차에는 보정 쪽 누적 P가 0.321자리 앞섰다. 환생 시점이 같은 이 사례는 스와이프·구매의 경로 차이를 추적할 후보다. [일별 원본](b-calibration-data/SoftReplay/Loop/daily.csv.gz) · [지급 원본](b-calibration-data/SoftReplay/Loop/prestige.csv.gz)

SoftReplay의 Lv60 재도달을 직전 환생으로 나누면 문제가 선명하다. **공장 초기화 뒤**에는 앞선 비율이 **99.2~100%**, 중앙값이 **0.009~0.031**이다. **GB 동기화 뒤**에는 앞선 비율이 **19.2~23.9%**, 중앙값이 **5~56**이다. 동기화가 파일 상점 강화와 파일층 수입을 초기화하면서, 기준 회차에서 이미 켜진 파일 강화의 속도를 새 회차에서 잃는다. Lv124 최초 도달은 Merit 0/180, Replay 178/180, SoftReplay 176/180이며, SoftReplay에서는 Lv124를 밟은 두 회차가 연속된 경우가 없었다. Lv124는 그 관측값으로만 유지한다. [Lv별 재도달](b-calibration-tables/SoftReplay/Loop/acceleration.csv) · [직전 환생 종류별 재도달](b-calibration-tables/SoftReplay/Loop/acceleration-transitions.csv)

초반 재도달에는 입력 해상도도 영향을 준다. SoftReplay 무광고 Lv12의 **8,290/9,889회**가 정확히 3초, 터보의 **9,376/10,822회**가 정확히 1초였다. 시뮬 플레이어는 배터리가 차 있어도 첫 입력을 `chargeMs`만큼 기다린다. 첫 스와이프에서 이미 도달한 회차가 거듭된 경우 수입을 올려도 이 모델에서는 같은 시각이 기록된다. 목표는 그대로 두고, 재검증에서는 실제 첫 입력 대기와 필요 스와이프 수를 함께 남겨 이 동률과 코어 성장 병목을 구분한다. [원본 재도달 시각](b-calibration-data/SoftReplay/Loop/reaches.csv.gz)

## 3. B안 실제 유료 효과

미보유·1세트·풀세트와 6프로필 × 30시드를 같은 조건으로 짝지었다. 각 플레이 패턴의 7·14·28·56일에 누적 P 차이를 본다. 각 시점의 판정은 평균 차이이며, 원자료에서 30시드 중 몇 개가 기대 수치에 도달했는지도 확인할 수 있다. 3스와이프 플레이어는 이미 가진 앱의 반복 강화만 산다. 새 앱 구매까지 하는 가벼운 플레이는 45초 실험이 더 잘 나타낸다.

| 접속량 | 목표 | 충족한 프로필·시점 | 무광고 56일 | 가장 중요한 미달 |
|---|---|---:|---:|---|
| 하루 15분 · 1세트 | +0.50자리 | **20/24** | +1.679자리 | 56일 화면 보상 패스 +0.337, 터보 +0.350, 울트라 +0.440 |
| 하루 15분 · 풀세트 | +1.00자리 | **23/24** | +4.018자리 | 56일 화면 보상 패스 **+0.99296** |
| 하루 45초 · 풀세트 | +0.25자리 | **11/24** | **+0.24989자리** | 무광고 56일은 반올림 전에 미달 |
| 하루 3스와이프 · 풀세트 | +0.25자리 | **12/24** | +0.606자리 | 56일 광고 −0.007, 울트라 −0.010자리 |

하루 15분의 56일 풀세트는 광고 +1.472, 월간 패스 +1.375, 터보 +1.236, 울트라 +1.291자리였다. 하루 15분의 터보 추월은 **1~56일 모두 0/30**이었다. 가벼운 두 패턴은 공식 네 시점에서 모두 0/30이었고, 3스와이프 첫날에는 아래 예외가 있었다. [15분 EX 판정](b-calibration-tables/Merit/Quarter/ex-gate.csv) · [45초 EX 판정](b-calibration-tables/Merit/Light45/ex-gate.csv) · [3스와이프 EX 판정](b-calibration-tables/Merit/Light3/ex-gate.csv) · [15분 터보 비교](b-calibration-tables/Merit/Quarter/turbo.csv)

풀세트 무광고와 EX 미보유 터보는 같은 시드를 하루마다 비교했다. 3스와이프의 첫날은 무광고 풀세트 **30/30**이 앞섰고 평균 **+0.125자리**였다. 2~56일은 **0/30**, 네 공식 비교 시점도 **0/30**이었다. 45초는 1~56일 모두 **0/30**이었다. 동일한 세 번의 입력만 허용하면 첫날에는 터보의 충전 속도 이점이 추가 스와이프로 바뀌지 않는다. 터보의 초회 체감이 필요하면 구독 첫 활성에 실제 진행 효용을 주고 B의 상시 효과는 유지하는 쪽이 맞다. [3스와이프 대조](b-calibration-tables/Merit/Light3/turbo.csv) · [45초 대조](b-calibration-tables/Merit/Light45/turbo.csv)

### SoftReplay에서 같은 B를 재측정

하루 15분에서 1세트는 **18/24**, 풀세트는 **23/24**였다. 풀세트가 미달한 광고 14일차는 **+0.9993자리**다. 56일 풀세트는 6프로필 전체에서 **+1.518~+4.594자리**였고 1세트도 전체 **+0.819~+2.480자리**였다. 무광고 풀세트의 터보 추월은 56일 내내 **0/30**이었다. [15분 짝 판정](b-calibration-tables/SoftReplay/Quarter/ex-gate.csv) · [터보 비교](b-calibration-tables/SoftReplay/Quarter/turbo.csv)

**보유 수량의 단조성은 두 곳에서 깨졌다.** 15분의 무광고 56일차는 풀세트가 한 세트보다 평균 **0.063자리**, 터보 28일차는 **0.021자리** 낮았다. 무광고 시드 16은 풀세트에서 파일과 GB의 누적이 각각 0.224자리와 0.477자리 더 컸고 회차가 30 대 27로 앞섰지만, 누적 P는 **0.660자리** 낮았다. 구매가 환생 빈도까지 바꾸므로, 판매 효과 판단에서 같은 환생 일정을 고정한 짝과 실제 환생 선택을 하는 짝을 둘 다 남겨야 한다. [풀세트와 한 세트 비교](b-calibration-tables/SoftReplay/Quarter/ex-full-vs-set.csv) · [일별 원본](b-calibration-data/SoftReplay/Quarter/daily.csv.gz)

SoftReplay의 45초 풀세트는 **20/24**였다. 미달 4개는 모두 7일차에서 나왔고, 14·28·56일은 6프로필이 모두 통과했다. 56일 무광고는 **+0.381자리**다. 3스와이프는 **12/24**로 같았으며, 광고·울트라의 56일 평균이 여전히 소폭 음수였다. 무광고 풀세트의 터보 추월은 45초의 모든 날에서 0/30, 3스와이프는 첫날 30/30 이후 0/30이다. [45초 짝 판정](b-calibration-tables/SoftReplay/Light45/ex-gate.csv) · [3스와이프 짝 판정](b-calibration-tables/SoftReplay/Light3/ex-gate.csv)

## 4. 복귀 화면 실측

실제 엔진에서 같은 풀세트의 6시간·15시간 복귀 응답을 만들고, 기본 상한은 12시간으로 맞췄다. 현행 페이지와 제안 패치를 적용한 페이지의 같은 복귀 핸들러에 각각 응답을 흘려 Chromium에서 비교했다. 현행은 EX 상세 영역이 없었다. 패치 후보는 두 문구가 실제 보였고 페이지 오류가 없었다.

| 부재 | 늘어난 상한 | 실제 추가 인정 | 이번 EX 추가 P |
|---|---:|---:|---:|
| 6시간 | +147분 | 0분 | 0 P |
| 15시간 | +147분 | 147분 | +1.76M P |

이 영역은 지급이 0이어도 상한과 이번 실효를 구분한다. P는 코어 문자열을 그대로 표시한다. 현재 운영 페이지를 바꾸지 않고 분리 화면에서 검증했다. [6시간 화면](b-calibration-data/screen/return-6h.png) · [15시간 화면](b-calibration-data/screen/return-15h.png) · [브라우저 판정](b-calibration-data/screen/rendered.json) · [offline-display.patch](../../tools/economy-sim/growth-loop/offline-display.patch)

시간 강화는 계속 성장한다. `기본 상한 = 레벨별 기본 시간 + 파일 오프라인 강화 Lv × 1시간 + GB 오프라인 강화 Lv × 1시간`이고 EX의 시간이 그다음에 더해진다. 파일 Lv12와 GB Lv7에서 기본 상한 **+19시간** 및 저장 복원값 일치를 확인했다. 파일 강화의 효율 보너스와 시간 보너스는 별개다. [코어 회귀 집계](b-calibration-data/core-tests.csv)

## 5. 권고

1. **SoftReplay를 후속 가격 실험의 기준으로 삼는다.** 싼 저단계는 유지하고, 1자리를 넘긴 6시드에서 각 분기점의 앱 구매·장착·정산 순서를 같은 입력으로 재생한다. 그래야 미세한 장부 차이와 다음 회차 선택이 바뀌면서 생긴 손실을 가를 수 있다. 새 강화 구간의 대기와 1자리 손실 기준을 함께 검증한다.
2. **GB 동기화 뒤의 파일층 수입 복구를 환생 가속의 첫 과제로 삼는다.** 유효 기록으로 확보한 파일층 수입의 일부를 Lv60까지 새 회차의 수입에 적용하고 이후에는 점진적으로 해제하는 대조를 권한다. 이미 시험한 전 구간 4제곱근 계승의 효과가 약했고, 반복 지급 자체를 키우면 초기화 한 번에 12개 이상 구매하는 문제를 키운다. 초기화의 초과 구매 수는 여러 회차에 걸친 파일 가격 기울기로 별도 보정한다. 구현 판단에는 동일 회차 선택을 고정한 재생과 플레이어가 선택하는 재생 모두에서 Lv12·25·60·124를 확인한다.
3. **B의 할인과 147분 오프라인 상한은 유지한다.** 가벼운 패턴에 실제 효용을 주려면 12시간 미만 복귀에도 동작하는 효과가 필요하다. 같은 시간 내 기존 오프라인 P에 상한이 있는 배수를 주는 대조를 권한다. 12시간을 넘긴 복귀는 시간 혜택을 기존대로 보여주고, 3스와이프 첫날에는 터보와 동일한 입력량에서도 구독 효과를 확인한다. 환생 규칙에 따라 같은 B 효과가 달라지므로 두 시스템을 바꿀 때는 같은 짝을 재검증한다.

## 6. 재현

보고 당시 소스가 있는 `dbdf26c`의 별도 워크트리에서 JDK 17로 실행한다. 원자료 파일은 각각의 하위 디렉터리에 `daily.csv.gz`, `prestige.csv.gz`, `reaches.csv.gz`라는 이름으로 보존했다. 파일별 해시는 압축 해제 기준이다.

```sh
git apply --unidiff-zero tools/economy-sim/precheck/integrated-core.patch
git apply --unidiff-zero tools/economy-sim/precheck/integrated-tests.patch
git apply --unidiff-zero tools/economy-sim/growth-loop/isolated-core.patch
git apply --unidiff-zero tools/economy-sim/growth-loop/cadence-core.patch
NC_OFFLINE_UI_OUT=/tmp/b-screen-core ./gradlew -p core test
cp tools/economy-sim/growth-loop/CadenceProbe.kt tools/economy-sim/src/main/kotlin/
./gradlew -p tools/economy-sim installDist
libs='tools/economy-sim/build/install/economy-sim/lib/*'
java -Xmx4g -cp "$libs" CadenceProbe /tmp/b-merit/Loop Loop Merit 30 168 4
for mode in Quarter Light45 Light3
do
  java -Xmx4g -cp "$libs" CadenceProbe "/tmp/b-merit/$mode" "$mode" Merit 30 56 4
  java -Xmx4g -cp "$libs" CadenceProbe "/tmp/b-soft-replay/$mode" "$mode" SoftReplay 30 56 4
done
java -Xmx4g -cp "$libs" CadenceProbe /tmp/b-replay/Loop Loop Replay 30 168 4
java -Xmx4g -cp "$libs" CadenceProbe /tmp/b-soft-replay/Loop Loop SoftReplay 30 168 4
java -Xmx4g -cp "$libs" CadenceProbe /tmp/b-focus-pilot/Loop Loop Focus 3 84 4
java -Xmx4g -cp "$libs" CadenceProbe /tmp/b-replay-pilot/Loop Loop Replay 3 84 4
java -Xmx4g -cp "$libs" CadenceProbe /tmp/b-inherit-pilot/Loop Loop Inherit 3 84 4
java -Xmx4g -cp "$libs" CadenceProbe /tmp/b-moderate-pilot/Loop Loop Moderate 2 56 2
python3 tools/economy-sim/growth-loop/analyze-cadence.py /tmp/b-merit /tmp/b-merit-tables --economy Merit
python3 tools/economy-sim/growth-loop/analyze-cadence.py /tmp/b-replay /tmp/b-replay-tables --economy Replay
python3 tools/economy-sim/growth-loop/analyze-cadence.py /tmp/b-soft-replay /tmp/b-soft-replay-tables --economy SoftReplay
awk 'FNR>1 || NR==1' /tmp/b-merit-tables/daily-growth.csv /tmp/b-replay-tables/daily-growth.csv /tmp/b-soft-replay-tables/daily-growth.csv > /tmp/daily-growth.csv
```

실행은 고정 시각과 시드 1부터 시작한다. 같은 실험 변수로만 출력 경로를 재사용해야 완료 경로를 이어갈 수 있다. SoftReplay의 무보정 경로는 Replay와 동일하다. 무광고·월간 패스의 시드 1에서 168일의 두 팔을 재실행하여 일별 672행, 지급 1,352행, Lv 로그 3,266행이 바이트 단위로 일치했다. Merit의 네 모드에서 별도로 154행도 일치했다. 코어 테스트 **91개**가 통과했다. [재실행 일치](b-calibration-data/replay-parity.csv) · [코어 회귀 집계](b-calibration-data/core-tests.csv)

`playwright`와 Chromium이 준비된 환경에서 아래 명령으로 화면을 검증한다. `CHROMIUM`으로 실행 파일을 지정할 수 있다. 화면 검증 범위는 Chromium의 실제 엔진 응답 재생이다. [화면 테스트](../../tools/economy-sim/growth-loop/offline-display-test.js)

```sh
cp -a app/src/main/assets/game /tmp/b-screen-current
git apply --unidiff-zero tools/economy-sim/growth-loop/offline-display.patch
python3 tools/game-page/build.py
node tools/economy-sim/growth-loop/offline-display-test.js \
  /tmp/b-screen-current/index.html app/src/main/assets/game/index.html /tmp/b-screen-core /tmp/b-screen-render
```
