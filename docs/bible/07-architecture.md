# 07 · 아키텍처

## 원칙

1. **판정은 순수 Kotlin 한 곳에서.** `core/`가 상태 전이를 확정하고, Android는 입력·저장·재생만 한다.
2. **결정론.** 같은 상태·입력·시각이면 같은 결과. 시간은 인자로, 난수는 상태 안의 `Long` 하나.
3. **데이터 주도.** 모든 수치는 `RuleSet`(공식 계수)과 `Catalog`(콘텐츠)에. 문서의 참조표는 그 데이터에서 생성.
4. **정확한 큰 수.** 점수·P는 `BigInteger`, 저장은 10진 문자열. 표시는 단위 문자열로.
5. **시뮬레이터가 먼저.** 경제 변경은 `tools/economy-sim`으로 8주를 돌려 본 뒤 코드에 확정.

## 모듈

```text
┌──────────────────────────┐   implementation   ┌───────────────────────────────┐
│ app (Android, Compose)   │ ─────────────────▶ │ core (JVM Kotlin, 의존성 없음)  │
│ · 화면/테마/연출/햅틱     │                    │ · Content: 앱·카드·판 강화·백업 │
│ · 입력 → Engine 호출      │                    │ · RuleSet: 계수·공식            │
│ · 저장/복원 (DataStore)   │                    │ · Engine: swipe/tick/offline/   │
│ · 광고·결제 SDK 어댑터     │                    │   buy/deck/reset                │
│ · 텔레메트리 송신          │                    │ · Beat: 재생용 결과 박자        │
└──────────────────────────┘                    └───────────────────────────────┘
                                                     ▲ srcDir 공유
                                   ┌─────────────────┴──────────────┐
                                   │ tools/economy-sim (JVM)         │
                                   │ · 회차·8주 일상 시뮬레이션       │
                                   │ · 구매·덱·초기화 정책            │
                                   └────────────────────────────────┘
```

현재 `app/`은 시연 앱(《알림 청소부 FX》)이고 자체 `GameEngine.kt`를 쓴다. 3단계 그레이박스에서 `app`을 `core`에 연결하고 시연 엔진은 삭제한다.

## 실행 흐름

```text
터치(스와이프 확정) ─▶ ViewModel.swipe(rowId)
   ─▶ Engine.swipe(state, rowId, now) ─▶ SwipeOutcome(state', beats, earned, chain, overload)
   ─▶ 저장 큐에 state' (거래 단위)  ─▶ 연출 타임라인이 beats를 재생 (스킵해도 state'는 확정)
앱 복귀 ─▶ Engine.settleOffline(state, 경과(단조 시계, 상한)) ─▶ 복귀 팝업(무료 100% / 광고 200%)
자동 정리 ─▶ 4.5초 타이머 ─▶ Engine.autoTarget ─▶ swipe(actor = Auto)
```

## 상태와 저장

| 항목 | 방식 |
|---|---|
| 저장 단위 | `GameState` 전체 + `Backup`(초기화에도 유지), JSON |
| 스키마 | `schema` 정수, 규칙 버전 `rulesVersion`. 이전 스키마 읽기 테스트 필수 |
| 큰 수 | 10진 문자열 |
| 저장 시점 | 스와이프 확정·구매·초기화·광고 보상마다 (거래 단위, 원자적 쓰기) |
| 시간 | **같은 부팅**이면 `elapsedRealtime` 차이, 재부팅·시간원 불연속이면 서버 마지막 검증 시각 또는 보수적 상한. 의심만으로 정상 사용자 보상을 몰수하지 않음 (조사 E4) |
| 저장 기록 | 종료·저장 때 `save_id · session_id · state_version · save_hash · wall_time · elapsedRealtime · 부팅 정보 · deck_snapshot · R_auto`를 원자적으로 (조사 E5) |
| 오프라인 | `B_off = min(t, 상한) × R_auto × 효율`, 광고 ×2는 별도 거래. 광고를 안 봐도 기본 전액 |
| 광고·결제 | `claim_id = hash(account, save_id, 구간 끝, 보상 종류)` 고유키로 멱등 지급. 재시작·중복 콜백·Auto Backup 복원에도 한 번만. Play Integrity는 결제·계정 동기화 같은 고가치 거래에만 |

## 큰 수 전략

- 현재 최댓값 약 10⁴⁰(8주 시뮬). `BigInteger` 연산은 스와이프당 수십 번이라 문제없음.
- 표시·연출(카운트업 60fps)은 `Double` 로그값 근사로 그린다.
- `BigInteger`는 상한이 없어 1.8e308 오버플로가 생기지 않는다(조사 B8의 BigDouble 권고는 거절). 성능만 프로파일링으로 감시.

## 원격 설정 (계획)

`RuleSet` + `Catalog`를 `rules-vN.json`으로 직렬화. 앱은 내장 기본값으로 시작하고, 원격 버전이 더 높고 서명이 맞으면 교체. 회차 진행 중에는 교체하지 않고 다음 초기화 때 적용(결정론 유지).

## 텔레메트리 (계획)

| 이벤트 | 필드 |
|---|---|
| `swipe_summary` (1분 묶음) | 스와이프 수, 폭발, 최대 연쇄, 과부하, P 자릿수 |
| `purchase` | 종류(앱·강화·프로·카드·판 강화·백업), 대상, 가격 자릿수, 레벨, 회차 |
| `deck_change` | 앱 수, 시너지 단계 |
| `level_up` / `unlock` | 레벨, 처음 열림 여부, 회차 경과 시간 |
| `factory_reset` | 회차 번호, 레벨, 누적 P 자릿수, 받은 파일, 회차 시간 |
| `offline_settle` | 경과, 상한, 지급 P 자릿수, 광고 선택 |
| `session` | 길이, 직접/자동 비율 |

시뮬레이터 지표와 같은 이름을 써서 실측과 시뮬레이션을 바로 비교한다.

## 테스트

| 층 | 도구 | 범위 |
|---|---|---|
| 규칙 | `./gradlew -p core test` (CI) | 고정 시드 예시, 덱·시너지·점화제·카드·초기화·배터리·오프라인 |
| 경제 | `./gradlew -p tools/economy-sim run --args="daily 56 4"` | 8주 지표, 해금 밀도 |
| 앱 | `./gradlew testDebugUnitTest lintDebug assembleDebug` + 에뮬레이터 스모크 (CI) | 시연 앱 (그레이박스로 교체 예정) |
| 기기 | 실기기 체크리스트 | 입력 지연, 프레임, 햅틱, 블러 대체 |

## 백로그 (아키텍처)

1. `app` → `core` 의존 연결, 시연 엔진 제거 (그레이박스).
2. 대량 구매 닫힌 식 `b·rᵏ·(rⁿ−1)/(r−1)`과 Max 계산을 `core`에.
3. 초기화 가속 예측 함수(예상 회차 시간).
4. `RuleSet`/`Catalog` JSON 직렬화와 버전 검증.
5. 저장 스키마 v1 + 마이그레이션 테스트 틀.
