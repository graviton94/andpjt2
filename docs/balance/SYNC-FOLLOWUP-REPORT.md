# SoftReplay 후속 검증 · 동기화 가속, EX, 강화 대기

**기준선**은 `ccr-balance-port`의 `dbdf26c` SoftReplay다. 사용자 결정에 따라 이식 판정은 환생 직후 재돌파, 매일 자릿수가 늘어나는 성장, 구매량에 따른 이득을 중심으로 본다. 운영 이식 전 실제 코어를 별도 워크트리에 적용해 상점·스와이프·복귀·환생을 그대로 실행했다. 화면 검증에는 화면 담당의 `5a64eeb` 에셋을 읽었다. [이전 기준선](B-CALIBRATION-REPORT.md) · [화면 계약](api-changes.md)

**운영 이식 보류.** 세 계승 곡선은 모두 동기화 직후 절대 시간을 줄였지만, 인접 회차 대비 20% 가속이 최소 70%여야 하는 조건과 공장 초기화 직후 가속을 함께 충족하지 못했다. 초반의 값싼 강화와 장기 자릿수 성장이 같이 살아나는지를 이식 판단에서 계속 확인한다.

## 현재 → 목표 → 결과

| 항목 | 현재 SoftReplay | 목표 | 이번 결과 |
|---|---|---|---|
| GB 동기화 직후 Lv60 시간비 ≤0.80 | 12.91~16.22% | 인접 쌍 70% 이상 | **미달**. 후보별 최고 42.00% |
| 공장 초기화 직후 가속 | 더 빠른 쌍 99.17~100% | 기존 수준 유지 | **미달**. 세 계승 후보 중 최고 44.49% |
| EX 풀세트 ≥1세트 | 15분 체크포인트 2곳, 전체 25곳 역전 | 전 프로필·날짜 0곳 | **미달**. ReturnGuard는 45초·3회 각각 0곳, 15분 22곳 |
| 45초 · 3스와이프 EX | 20/24 · 12/24 | ≥22/24 · ≥18/24 | **통과**. ReturnGuard 24/24 · 19/24 |
| 무광고 풀세트의 터보 추월 | 3회 첫날 30/30, 나머지 0/30 | 0/30 | **통과**. ReturnGuard 세 패턴 모두 1~56일 0/30 |
| P 강화 대기 | 견적 2.25~7.10%, 실구매 대조 초반 2.67%·후반 1.00% | 초반·후반 각 70% 이상 | **통과**. PowerFollow 실구매 99.91% · 96.28% |
| 초기화 1회로 강화 3~8개 | 30.38% | ≥60% | **통과**. PowerFollow 30,959/32,735 · 94.57% |
| Lv124 인접 회차 | 0쌍 | ≥30쌍 | **확보**. 336일 SoftReplay에서 9,049쌍 |
| 코어 회귀 · 15시간 복귀 | 코어 91개 · 이전 화면 시제품 | 전부 통과 · 화면 재현 | **통과**. 95/95 · 화면 담당 `5a64eeb` 에셋에서 재현 |

## 1. GB 동기화 후 수입 계승

**계승 원금**은 유효한 파일·GB 지급으로 마친 회차의 자연 획득 P 기록 `A = max(기존 기록, 회차 P − 이번 회차에서 지급한 계승 P)`다. 자연 획득 P가 Lv25에 해당하는 기준 P 이상일 때 기록한다. `F`를 `A`로 받을 기본 파일 수를 가정한 기존 파일 수입 계수라고 두고, `A × (F−1)/F`를 파일층 수입 기여분의 추정값으로 쓴다. 상품 배율·활동 지급 바닥으로 계승 원금을 따로 올리지 않는다.

정책별 시작 비율을 `s`, 수입을 나눌 기준 초를 `T`, 감쇄가 시작되는 레벨을 `L₀`라고 한다. 현재 Lv가 `L`일 때 기준 초당 P는 아래와 같다.

```
시작 P/초 = floor(A × (F − 1) / F × s / T)
남은 비율 = clamp((60 − max(L, L₀)) / (60 − L₀), 0, 1)
지급 = min(floor(시작 P/초 × 남은 비율 × 인정한 스와이프 시간), Lv60까지 남은 P, 스와이프 상한 여유)
```

유효한 수동 스와이프에 회당 최대 12초를 인정한다. 같은 타임스탬프에서 재차 보너스를 지급하지 않는다. 자동·도전에는 적용하지 않으며, 계승 지급 자체는 파일 기록이나 최근 분당 수입에 포함하지 않는다. Lv60 또는 첫 회차 종료에서 지급이 끝난다.

| 초당 계승 곡선 | 무광고 | 광고 | 패스 | 화면 패스 | 터보 | 울트라 | 초기화 직후 더 빨랐던 비율 |
|---|---:|---:|---:|---:|---:|---:|---:|
| SoftReplay 기준선 | 15.33% | 16.22% | 14.14% | 14.15% | 15.39% | 12.91% | 99.17~100% |
| Linear35 · 35%, 60초 기준, Lv1부터 선형 | 15.75% | 25.62% | 38.90% | **42.00%** | 13.86% | 12.24% | 0~44.49% |
| Hold65 · 65%, 30초 기준, Lv35부터 선형 | 18.87% | **34.36%** | **41.85%** | 39.47% | **16.39%** | 14.66% | 0~34.59% |
| Hold90 · 90%, 12초 기준, Lv50부터 선형 | **19.12%** | 30.01% | 38.64% | 34.92% | 16.05% | **14.75%** | 0~34.77% |

프로필 열에는 **동기화 직후 Lv60 시간비 ≤0.80**의 비율을 적었다. 30시드·168일의 새 회차 Lv60 중앙시간은 세 후보 모두 **1~3초**로 기준선의 **44~516초**보다 짧았다. 바로 앞 회차도 첫 입력에 지나가는 경우가 늘어 상대 시간비 중앙값은 모든 후보·프로필에서 **1.00**이다. 세 후보 모두 180/180시드가 마지막 28일 성장과 정체일 기준을 지켰다. [말기 성장 원표](sync-followup-tables/tail.csv)

측정은 직전 회차와 새 회차가 같은 Lv를 실제로 밟은 인접 쌍을 대상으로 한다. `5×새 시간 ≤ 4×직전 시간`이면 20% 이상 빨라진 쌍이다. 직전 시간이 0인 비율 불능 쌍과 동률을 별도 집계한다. 공장 초기화는 기존의 `새 시간 < 직전 시간` 지표로 비교한다. [프로필·환생 종류별 원표](sync-followup-tables/acceleration.csv)

기준선에서 무광고 동기화 쌍 **667/1,096**의 직전 Lv60 시간이 3초였고, 터보 **1,042/1,845**는 1초였다. 둘 다 해당 프로필의 시뮬레이션 첫 입력에 해당한다. 다음 회차도 첫 입력에 Lv60을 밟으면 시간비는 1이다. 이어받는 P를 올려도 입력 전에는 지급하지 않는 구조라 이 쌍은 수입만으로 0.80을 달성할 수 없다. 기준선과 같은 조건을 유지해 이 바닥 효과를 숨기지 않았다. 단일 스와이프로 Lv60에 닿게 하는 큰 계승량은 다음 공장 초기화의 상대 가속도 희석했다. [재도달 로그와 집계](sync-followup-tables/acceleration.csv)

**Lv124 재도달 표본은 확보했다.** 동일한 SoftReplay 일정에서 6프로필 × 6시드를 336일까지 연장해 **9,049쌍**을 얻었다. 동기화 직후 6,250쌍, 공장 초기화 직후 2,799쌍이다. 모든 프로필에서 두 종류 모두 표본이 생겼다. Lv124 동기화의 시간비 ≤0.80은 프로필별 **7.2~27.0%**였으므로 표본 확보와 가속 판정은 구분한다. [336일 재도달 원본](sync-followup-data/Long336/Loop/reaches.csv.gz)

## 2. EX B안

강화 할인과 풀세트 **+147분**은 동일하다. `K`를 보유 SKU 수, `Bₜ`를 기존 시간 상한 내 정산액, `P₀`를 시간 확장까지 적용한 기존 B 지급액으로 둔다.

| 후보 | 실제 복귀 지급 수식 |
|---|---|
| Return60 | `min(기존 회차 상한, P₀ + Bₜ × (0.06 × K))` |
| Return120 | `min(기존 회차 상한, P₀ + Bₜ × (0.12 × K))` |
| ReturnMemory | 유효 환생에서 기록한 EX 미포함 분당 수입과 현재 수입 중 큰 값으로 `Bₜ`를 계산. `P₀ + Bₜ × (0.16 × K)`를 지급하고 상한은 기존 값에서 다음 한 단계 여유의 `0.09 × K`만큼 확장 |
| ReturnGuard | ReturnMemory에 각 계정 첫 유효 속도 구독에서 P를 얻는 수동 입력 3회 ×2. 정기 갱신과 환생은 횟수를 복원하지 않음 |

다음 표의 자릿수는 동일한 시드의 차이다. 구매량 단조성은 7·14·28·56일 이외에도 **56일 전체**의 프로필별 평균을 검사했다. 개별 시드에서 풀세트가 낮았던 횟수도 원표에 따로 기록했다. 45초와 3스와이프의 풀세트 목표는 미보유 대비 평균 +0.25자리다. [모든 날짜 EX 짝 비교](sync-followup-tables/ex.csv) · [터보 일별 비교](sync-followup-tables/turbo.csv)

| 후보 | 15분 1세트 / 풀세트 충족 | 45초 풀세트 | 3스와이프 풀세트 | 날짜×프로필 평균 역전 건수 15분 / 45초 / 3회 | 무광고 풀세트의 터보 추월 |
|---|---:|---:|---:|---:|---|
| SoftReplay | 18/24 · 23/24 | 20/24 | 12/24 | 25 / 6 / 10 | 3회 첫날 30/30 |
| Return60 | 표본 없음 | 20/24 | 15/24 | — / 0 / 11 | 3회 첫날 30/30 |
| Return120 | 표본 없음 | 20/24 | 15/24 | — / 0 / 30 | 3회 첫날 30/30 |
| ReturnMemory | 2/24 · 8/24 | **24/24** | **19/24** | 22 / **0** / **0** | 3회 첫날 30/30 |
| ReturnGuard | 2/24 · 8/24 | **24/24** | **19/24** | 22 / **0** / **0** | **세 패턴 모두 1~56일 0/30** |

이전 보고서의 15분 역전 2곳은 네 체크포인트에서만 센 수치다. 56일 전체에서는 기준선 25곳, ReturnGuard 22곳이다. ReturnGuard의 22곳은 모두 화면 보상만 쓰는 패스의 **35~56일차**였다. 그 프로필의 56일차 풀세트는 미보유보다 평균 **14.663자리** 낮아서 구매량 간 역전보다 큰 문제가 있다. 무광고 수치까지 포함해 모든 프로필의 유료 효용이 보장되는 상품으로 보기는 어렵다.

시드 1의 화면 패스는 28일차 최고 Lv가 미보유 66, 한 세트 61, 풀세트 60이었다. 56일차는 각각 **91·63·60**이었다. 세 구성 모두 56일차 회차 수가 33이었고 최고 Lv와 누적 재화는 벌어졌다. 복귀 직후의 보정을 모든 접속 패턴에 같은 강도로 지급하면 순서가 바뀌어, 환생을 비슷하게 반복해도 깊은 수입 구간에 안착하지 못할 수 있다. [시드 1을 포함한 일별 원자료](sync-followup-data/ReturnGuard/Quarter/daily.csv.gz)

다음 실험에서는 복귀 효용을 실제 부재 시간과 이전 정산 이후 유효 플레이에 따라 좁혀 적용한다. 15분의 기존 성장 경로도 함께 고정 비교해, 가벼운 이용자 혜택이 긴 세션의 환생 선택을 크게 바꾸는지 측정할 필요가 있다. 첫 세 번 ×2는 터보 첫날 역전을 해결했지만, 상용 구독 혜택으로 정할 때까지 후보로만 둔다.

## 3. P 대기와 파일 구매력

견적은 활성 덱에서 방금 확인한 다음 가격이다. **실구매**는 구매에 성공한 시점의 실제 지출 전 가격을 당시 엔진의 최근 P/분으로 나눴다. 회차의 처음 10활성 분은 3–60초, 이후는 1–10분 구간으로 본다. P/분이 0인 시점은 나눗셈을 할 수 없어 집계에서 제외한다. `BaselinePurchases`는 동일한 계측으로 재실행한 SoftReplay 8시드 대조다.

| 후보 | 가격·지급 식 |
|---|---|
| Paced | 강화 3랭크 이상·5번째 스와이프부터 가격에 최근 최고 P/분의 초반 0.25분, 후반 2분에 해당하는 최저가를 적용. 파일 반복 가격의 성장률은 `1 + 3×(기존 기울기−1)` |
| FileGrowth | 파일 성장률만 Paced와 동일하게 적용 |
| PriceFollow | 첫 유효 환생 이후 가격의 최저가를 `min(회차 최고 P/분, 최근 P/분 × 1.25) × 목표 분`으로 산정. 과거 유효 파일 원금의 1/5을 다음 파일 상점의 반복 강화 최저가로 적용. 파일 기본 기울기는 유지 |
| FileMemory | 반복 파일 가격에 `max(현재 주기의 이전 원금, 직전 동기화에서 확정한 원금) / 5` 하한을 적용. 이전 활동 보상만 반복해서는 기준 원금이 올라가지 않음 |
| PowerFollow | FileMemory의 파일 가격, PriceFollow의 P 가격을 사용. 앱 강화 가치 기하 증가율은 ×1.20에서 ×1.50으로 변경 |

가격은 기존 견적과 새 최저가 중 큰 값을 쓰고 기존 할인은 그 이후에 적용한다. 파일 원금은 직전 지급을 더하기 전의 값을 사용한다. 플레이로 채운 지급 바닥과 해당 최소가의 갱신에 같은 견적을 써서 지급 직후에 실제 구매 가능한 수를 계산한다. [P 대기 원표](sync-followup-tables/waits.csv) · [파일·GB 지급 원표](sync-followup-tables/payouts.csv) · [같은 시드 누적 P 손실](sync-followup-tables/p-loss.csv)

| 가격 후보 | 시드/프로필 | 초반 실구매 충족률 | 후반 실구매 충족률 | 파일 지급의 3~8개 구매 | SoftReplay 대비 최대 P 손실 |
|---|---:|---:|---:|---:|---:|
| SoftReplay 실구매 대조 | 8 | 2.67% | 1.00% | 30.46% | 0.00자리 |
| FileGrowth | 8 | 1.77% | 1.05% | 19.02% | 0.95자리 |
| Paced | 30 | 55.25% | 72.09% | 24.16% | 9.26자리 |
| PriceFollow | 30 | 99.99% | 97.93% | 31.61% | 12.80자리 |
| FileMemory | 8 | 2.62% | 1.01% | 97.48% | 1.60자리 |
| **PowerFollow** | **30** | **99.91%** | **96.28%** | **94.57%** | **손실 0. 최소 우위 +2.44자리** |

PowerFollow의 6프로필 모두 후반 대기 **93.20% 이상**, 초기화 지급의 3~8개 구매 비율 **88.86% 이상**이었다. 동기화 지급으로 강화 2~5개를 살 수 있었던 비율은 **99.78%**다. 180시드 모두 마지막 28일 성장 기준을 지켰고 정체일은 0이다. 프로필별 하루 성장량 중앙값은 **+0.483~+0.502자리**였다. 대기 가격만 높인 PriceFollow가 최대 12.80자리를 잃은 것에 비해, 강화당 실제 가치와 파일 상점의 구매력을 함께 맞춘 PowerFollow는 모든 시드·관측일에서 기준선보다 누적 P가 높았다. 초기화 구매력에는 파일 지수보다 직전 동기화 원금의 상점 반영이 더 직접적으로 작동했다. P·파일 재검증의 후속 기준은 PowerFollow를 권한다. [동일 시드 P 차이](sync-followup-tables/p-loss.csv) · [말기 성장](sync-followup-tables/tail.csv)

## 4. 회귀 검증과 남은 위험

코어 테스트는 **95/95 통과**했다. 동기화 계승 유효성·만료·저장 복원, 동일 시각 중복 지급 억제, 기존 지급 필드 유지, 강화 견적과 지출 일치, 속도 구독 첫 세 번 지급을 검사했다. 세 계승 곡선은 각 프로필의 시드 1을 새 실행으로 다시 비교해 일별·지급·Lv 원본의 공통 열 **28,293행**이 모두 일치했다. [테스트 원본](sync-followup-data/core-tests.csv) · [시드 재현 해시](sync-followup-data/replay-parity.csv)

화면 담당의 `5a64eeb` 빌드와 실제 코어 정산 JSON으로 Chromium 복귀 카드가 열리는 것을 확인했다. 15시간 부재는 **35.2M P**, **EX 2시간 27분**, **상한 초과 33분**으로 표시됐다. 6시간 부재에서는 추가 인정 시간 0이지만 정산 P가 **16.7M**으로 늘었다. 테스트 브라우저 오류는 0건이다. [15시간 화면](sync-followup-data/screen/return-15h.png) · [6시간 화면](sync-followup-data/screen/return-6h.png) · [실제 응답과 렌더 로그](sync-followup-data/screen/rendered.json)

- **동기화 수입 복구는 일괄 P 대신 단계별로 작게 검증한다.** 기준 입력 안에 첫 Lv60이 이미 들어오는 횟수가 많다. 먼저 첫 입력 속도, 첫 입력에서 Lv60까지 넘긴 단계 수, 이전 회차가 첫 입력 이후에 Lv60을 밟았을 때의 재도달을 각각 기록하되, 사용자 합격선은 인접 회차 전체에 그대로 적용한다. 파일층 파워는 새 회차의 몇 차례 강화에 나눠 체감시키는 쪽이 공장 초기화의 이유를 살린다.
- **복귀 카드의 표시 금액은 화면 담당과 맞춰야 한다.** 기존 `basePaid`는 광고 가산 전 총액으로 EX 지급을 포함한다. 현재 카드가 여기에 `exExtraPShort`도 별도 줄로 보여주므로 두 줄을 더해 읽을 수 있다. 짧은 부재의 EX 추가 P도 시간 추가가 0이면 별도 줄이 나오지 않는다. 필드 계약을 유지하고 정산 내역의 의미를 화면 계약에 기록했다.
- **가격 후보와 EX·계승의 결합 결과는 별도 검증이 필요하다.** PowerFollow는 단독으로 P와 파일 목표를 통과했다. 첫 환생 이후의 저랭크에도 수입 기준 가격이 적용되므로, 같은 P 대기에서 초반 앱 교체와 강화 선택이 얼마나 달라지는지 확인할 필요가 있다.
- **구매량 효과에는 같은 게임 시간으로도 편차가 있다.** 프로필·날짜 평균과 개별 시드 역전은 따로 집계했다. 가벼운 3스와이프 패턴은 기존 소유 앱의 강화만 골라 사며, 45초 패턴은 새 앱 구매까지 한다. Android 기기 계측은 운영 이식 검수에 남아 있다.

## 5. 재현과 원본 파일

이 브랜치의 보고 커밋에서 별도 워크트리를 만든다. JDK 17 환경에서 아래 순서로 운영 기준에 후보를 적용한다. 앱과 화면 파일은 검증 과정에서 화면 담당 커밋으로부터 읽는다.

```sh
for patch in \
  tools/economy-sim/precheck/integrated-core.patch \
  tools/economy-sim/precheck/integrated-tests.patch \
  tools/economy-sim/growth-loop/isolated-core.patch \
  tools/economy-sim/growth-loop/cadence-core.patch \
  tools/economy-sim/growth-loop/sync-followup-core.patch
do
  git apply --unidiff-zero "$patch"
done
NC_SYNC_UI_OUT=/tmp/sync-receipts ./gradlew -p core test
cp tools/economy-sim/growth-loop/CadenceProbe.kt tools/economy-sim/src/main/kotlin/
./gradlew -p tools/economy-sim installDist
libs='tools/economy-sim/build/install/economy-sim/lib/*'
profiles=Free,Ads,Pass,PassVisible,Spend30,Spend100
for trial in Linear35 Hold65 Hold90 Paced PriceFollow PowerFollow
do
  java -Xmx4g -cp "$libs" CadenceProbe "/tmp/sync-runs/$trial/Loop" Loop "$trial" 30 168 4 1 "$profiles" frontier-only
done
for trial in FileGrowth FileMemory
do
  java -Xmx4g -cp "$libs" CadenceProbe "/tmp/sync-runs/$trial/Loop" Loop "$trial" 8 168 4 1 "$profiles" frontier-only
done
java -Xmx4g -cp "$libs" CadenceProbe /tmp/sync-runs/BaselinePurchases/Loop Loop SoftReplay 8 168 4 1 "$profiles" frontier-only
java -Xmx4g -cp "$libs" CadenceProbe /tmp/sync-runs/Long336/Loop Loop SoftReplay 6 336 4 1 "$profiles" frontier-only
for trial in Return60 Return120 ReturnMemory ReturnGuard
do
  for mode in Light3 Light45
  do
    java -Xmx4g -cp "$libs" CadenceProbe "/tmp/sync-runs/$trial/$mode" "$mode" "$trial" 30 56 4
  done
done
for trial in ReturnMemory ReturnGuard
do
  java -Xmx4g -cp "$libs" CadenceProbe "/tmp/sync-runs/$trial/Quarter" Quarter "$trial" 30 56 4
done
python3 tools/economy-sim/growth-loop/analyze-sync-followup.py /tmp/sync-runs docs/balance/b-calibration-data/SoftReplay /tmp/sync-tables
```

원본 `daily.csv.gz`, `prestige.csv.gz`, `reaches.csv.gz`는 후보·접속 패턴별 하위 디렉터리에 있다. 해시는 압축 해제한 내용으로 검증한다. 첫 측정에서 실구매 열을 수집하지 않은 경우 마지막 네 열은 비어 있거나 원본에 없다. 그 파일을 재실행본과 바이트 단위로 대조할 때는 헤더를 기준으로 일별 CSV의 공통 앞 21열만 비교한다. 15분 ReturnMemory의 미보유 경로 71개에서 기준선 공통 열과 지급·재도달 원본이 모두 일치했다. 나머지 미보유 109개는 EX 효과가 발동하지 않는 동일 SoftReplay 경로를 재사용했다. ReturnGuard 15분의 비속도구독 4프로필은 ReturnMemory 경로와 동일하다. [원자료 경로·해시](sync-followup-data/manifest.json) · [독립 재실행 대조](sync-followup-data/replay-parity.csv)

화면 담당 커밋의 게임 에셋을 별도 위치에 풀어 Chromium에서 실제 코어 응답을 재생한다.

```sh
mkdir -p /tmp/sync-screen-source
git archive 5a64eeb app/src/main/assets/game | tar -x -C /tmp/sync-screen-source
node tools/economy-sim/growth-loop/sync-return-test.js \
  /tmp/sync-screen-source/app/src/main/assets/game /tmp/sync-receipts /tmp/sync-screen-render
```
