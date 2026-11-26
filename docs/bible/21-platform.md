# 21 · 플랫폼 연동 (광고 · 결제 · 원격 설정 · 분석)

17장 BM을 실제 SDK에 잇는 설계. 원칙: **지급 판정은 core, SDK는 "보여 주고 확인"만.** 모든 지급은 `claim_id`로 멱등, 저장과 같은 거래(18장).

## 1. 구조

```text
app/platform/
├── Ads.kt            보상형 광고: 로드·표시·보상 콜백 → RewardClaim
├── Billing.kt        Play Billing: 데이터 팩(소모품)·스타터 팩(1회)·패스 3종(구독)
├── Claims.kt         지급 대기열: claim_id → Shop 호출 → 저장 → 확정(acknowledge/consume)
├── RemoteRules.kt    원격 설정 → RuleSet 덮어쓰기 (버전 키)
└── Analytics.kt      이벤트 송신 (18장 목록)
```

`GameViewModel`만 `Claims`를 부른다. UI는 버튼과 상태만.

## 2. 보상형 광고

| 항목 | 설계 |
|---|---|
| SDK | AdMob + 미디에이션(AppLovin·Unity·Pangle 순 워터폴/비딩). 보상형만, 전면·배너 없음 |
| 지면 → `AdSlot` | 최고 기록 ×2 · 오프라인 ×2 · 수입 부스트 · 오버클럭 · 시간 도약 · 보조배터리 · 데이터 상자 · 무료 강화 |
| 흐름 | 버튼 노출 조건 = `Shop.adsLeft > 0` (+ 지면별 조건) → 광고 로드 완료일 때만 버튼 활성 → 표시 → `onUserEarnedReward` → `claim_id = "ad:{slot}:{day}:{n}"` → `Shop.watchAd` → 저장 |
| 서버 검증 | 1단계는 클라이언트 콜백. 소프트런치에서 SSV(서버 측 보상 확인) 켬, 부정 비율 보고 결정 |
| 패스 보유 | 광고를 띄우지 않고 같은 `Shop.watchAd` 호출 (상한 동일). 버튼 문구만 "받기" |
| 실패 | 로드 실패 3회 → 버튼 숨김 30초. 표시 중 앱 종료 → 보상 콜백이 왔으면 다음 실행에 `Claims`가 지급 |
| 빈도 | 같은 지면 연속 표시 사이 최소 30초. 하루 상한은 core가 지킨다 |
| 동의 | UMP(맞춤 광고 동의), 아동 대상 아님 표기, Data safety 일치 |

## 3. 결제 (Play Billing)

| 상품 id | 종류 | 지급 (core) |
|---|---|---|
| `data_100` … `data_13500` (6종) | 소모품 | `Shop.credit(bought = true)`, 첫 구매 2배는 상품별 "처음 산 적 있음" 플래그 |
| `starter_pack` | 1회 (비소모) | 데이터 500 + 시작 앱 ★1 + 테마 |
| `growth_25/60/100` | 1회, 72시간 노출 | 데이터 + 백업 부스트 + 오버클럭 티켓 |
| `pass_monthly` | 구독 30일 | `Shop.buyPass` |
| `pass_turbo` | 구독 30일 | `Shop.buySpeedPass(Turbo)` |
| `pass_ultra` | 구독 30일 | `Shop.buySpeedPass(Ultra)` |

- **지급 순서**: `onPurchasesUpdated` → `purchaseToken`을 `claim_id`로 → core 지급 → **저장 완료 후에만** `consume`(소모품) / `acknowledge`(구독·1회). 저장 전에 죽으면 다음 실행에 `queryPurchasesAsync`로 미확정 구매를 다시 지급(멱등).
- **구독 상태**: 갱신·만료·유예기간은 Play가 원천. 실행마다 `queryPurchasesAsync(SUBS)`로 `passUntil`/`speedPassUntil`을 맞춘다(core 값은 캐시). 업그레이드(월간 → 터보 → 울트라)는 `ReplacementMode.CHARGE_PRORATED_PRICE`.
- **환불·취소**: Voided Purchases API를 서버(소프트런치 이후)로 확인해 데이터 회수(음수 잔액 허용, 상점 잠금). 출시 1단계는 로컬 기록만.
- **가격 표기**: Play의 현지화 가격 문자열을 그대로 표시. 원화 하드코딩 금지.
- **미성년·정책**: 구독 해지 방법·남은 일수, 확률형 없음 표기, 청약철회 안내(17장 11).

## 4. 지급 대기열 (Claims)

```text
claim(id, kind, payload)
  ├─ 이미 처리한 id? → 무시 (멱등)
  ├─ core 지급 (Shop/Meta 호출) → 새 GameState
  ├─ 저장 (GameState + processedClaims에 id 추가, 한 거래)
  └─ SDK 확정 (consume / acknowledge / 광고는 없음)
```

`processedClaims`는 최근 500개만 저장(오래된 구매 토큰은 Play가 다시 주지 않는다). 저장 형식은 `SaveCodec`과 같은 JSON.

## 5. 원격 설정

| 키 | 덮어쓰는 것 | 기본값 |
|---|---|---|
| `rules_v` | 규칙 버전 (앱보다 높으면 무시) | 3 |
| `rules_json` | `RuleSet` 일부 필드 (시뮬의 `SIM_RULES`와 같은 키 이름) | 내장값 |
| `packs` | 켤 콘텐츠 팩 | 없음 |
| `ads_enabled` / `ad_caps` | 지면 끄기·상한 조정 | 켬 / 17장 |
| `store_layout` | 상점 진열 순서·A/B 그룹 | 기본 |

같은 키 이름을 시뮬(`SIM_RULES`)과 원격 설정이 공유한다 → 시뮬로 검증한 값을 그대로 배포.

## 6. 분석 이벤트

18장 목록 + 수익화:
`ad_offer(slot)`, `ad_show(slot)`, `ad_reward(slot)`, `ad_fail(slot, code)`, `iap_view(sku)`, `iap_start(sku)`, `iap_success(sku, won)`, `iap_fail(sku, code)`, `sub_state(pass, active, until)`, `premium_up(app, star)`, `attend(day, streak)`, `mission_done(day)`, `overclock_use(source)`.

대시보드 지표: ARPDAU(광고·결제 분리), 결제 전환율, 첫 결제까지 일수, 광고 시청 분포(일 0/1~4/5+), 패스 유지율, 프로필별 Lv124 도달 일수(시뮬 목표와 비교).

## 7. 테스트

- core: 지급 함수 단위 테스트(이미 있음: 광고 상한·출석·패스·속도 구독·환생 배율).
- app: 가짜 Ads/Billing 구현으로 "지급 → 저장 → 확정" 순서와 재실행 복구를 계측 테스트.
- 내부 테스트 트랙에서 실제 결제(테스트 카드)·구독 갱신 가속(5분 = 1달)으로 만료·갱신 흐름 확인.
