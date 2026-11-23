# 22 · 그레이박스 작업 계획 (3단계)

목표: **core 엔진을 그대로 쓰는 실제 안드로이드 앱**으로 "밀고 → 터지고 → 정산 쾅"의 손맛과 하루 루프(출석·오프라인·초기화)를 폰에서 확인한다. 그림은 회색 상자여도 되지만 **타격감과 숫자 연출은 시연판 수준**이어야 한다(이 게임은 손맛 원툴).

빌드 환경: 이 클라우드 작업 공간은 안드로이드 SDK 다운로드 호스트가 막혀 있다. 로컬 PC(Android Studio) 또는 CI(`.github/workflows/android.yml`, main 푸시·PR)에서 빌드한다. core는 어디서나 `gradle -p core test`.

## 1. 모듈 연결

- `settings.gradle.kts`: `include(":core")` 추가, `app`이 `implementation(project(":core"))`. core는 이미 순수 Kotlin(JVM 17) + kotlinx.serialization.
- 시연 앱 코드(`GameEngine.kt`, `GameSession.kt`, `NotificationShade.kt` …)는 `app/legacy/`로 옮기고 빌드에서 뺀다. `ImpactFeedback.kt`(햅틱)만 참고해 `fx/Haptics.kt`로 다시 쓴다.

## 2. core API ↔ 앱 대응표

| 앱 쪽 | core |
|---|---|
| `GameViewModel.swipe(rowId)` | `Engine.swipe` → `Meta.record` → `SaveCodec.encode` 저장 → `FxPlan.plan` 재생 |
| 자동 정리 타이머 | `Engine.autoIntervalMs(s, now)` + `Engine.autoTarget` + `swipe(actor = Auto)` |
| 배터리 | `Engine.tick`, `chargeMs(s, now)` |
| 복귀 | `AwayClock.awayMs` → `Engine.settleOffline` → 오프라인 ×2 광고 제안 `Shop.watchAd(OfflineDouble, offerP)` |
| 상점 · 강화 | `Engine.buy`, `upgrade/upgradeMany/upgradeMax`, `upgradeCost` |
| 덱 편집 | `Engine` 덱 함수, `rules.maxSlots` |
| 공장 초기화 · 동기화 | `Meta.reset` / `Meta.sync`, 미리보기 `backupFilesNow`, `cloudGbNow` |
| 백업 · 클라우드 상점 | `buyBackup`, `buyCloud`, `backupOpen`, `cloudOpen` |
| 데이터 · 프리미엄 ★ | `Shop.upgradePremium`, `premiumPrice`, `useStarVoucher` |
| 광고 지면 | `Shop.adsLeft`, `watchAd` (패스면 광고 없이) |
| 데이터 상점 · 티켓 | `Shop.buyItem`, `useTicket` |
| 패스 · 속도 구독 | `Shop.buyPass`, `buySpeedPass`, `claimPassDaily`, `passActive` |
| 결제·광고 지급 | `Shop.claimOnce(claimId)` (21장 지급 대기열) |
| 출석판 · 미션 · 업적 · 도전 | `Shop.claimAttendance`, `Meta.missionsFor/claimMissions`, `Meta.earned`, `Meta.startChallenge/finishChallenge` |
| 숫자 표기 | `short(BigInteger)` |
| 연출 | `FxPlan.plan(outcome, reducedMotion)` → `fx/Director` |
| 효과음 | `assets-src/sfx/*.ogg` → `app/src/main/res/raw` (SoundPool) |

## 3. 화면 (그레이박스 범위)

| 화면 | 내용 | 비고 |
|---|---|---|
| 판 | 상태표시줄(장식) · 지갑(점수 층) · 칩×배율 · 알림 카드 7~12장 · 하단 장식 버튼 | 06 UX, 시연판 배치 그대로 |
| 끌어내리는 패널 | 상점 · 강화(×1/×10/Max/모두) · 덱 · 카드 · 보조 | 회색 상자 + 텍스트 |
| 초기화 · 동기화 | 받을 재화 미리보기, 확인 | 16 온보딩 문구 |
| 백업 · 클라우드 상점 | 목록 | |
| 데이터 상점 · 프리미엄 | 앱별 ★, 상품 목록(가짜 결제) | `ui-assets` 아이콘 사용 |
| 출석판 · 미션 · 업적 | 30칸 판, 미션 3개, 칭호 | |
| 오프라인 정산 팝업 | 번 P + ×2 버튼 | |
| 설정 | 소리·진동·움직임 줄이기·숫자 표기, **게임 데이터 초기화**, 정보(앱 인스턴스 ID 표시) | 개인정보처리방침 데이터 삭제 절에 약속한 기능 |

## 4. FX 감독 (fx/)

- `Director`: `Cue` 목록을 프레임 시계로 재생. 히트스톱 동안 시뮬 시간 정지, 슬로모, 입력은 계속 받음(연출 빨리감기).
- 두 층: 세계 층(배경 블롭·카드·파편·빛) → `RuntimeShader`(AGSL, Android 13+) 후처리(충격파·화면 금·아지랑이·물결·RGB 분리·글리치·CRT), 그 위에 **점수 층**(왜곡 없음). 12 이하는 셰이더 없이.
- 파티클 풀 600(저사양 300), 120Hz 고정 스텝, 시드 난수. 카드 유리 깨짐은 카드 스냅숏 비트맵을 거미줄 다각형으로 잘라 그림.
- 글자: 디스플레이 글꼴 + 잉크 외곽선·딱딱한 그림자·세로 그라데이션(시연 `gtext`).
- 소리: SoundPool 동시 24, id별 음량표(무게 사다리), 같은 소리 30ms 합침.

## 5. 마일스톤

| M | 내용 | 완료 기준 |
|---|---|---|
| M1 | 모듈 연결 + 판 화면(회색) + 스와이프·합치기·폭발 + 저장/복원 | 앱을 죽였다 켜도 판·지갑 그대로 |
| M2 | FX 감독 1차: 히트스톱·흔들림·파편·칩×배율·정산 카운트업·효과음 | 시연판과 나란히 놓고 손맛 비교 |
| M3 | 패널: 상점·강화·덱·초기화·백업 상점·오프라인 정산 | 시뮬 무과금 1주 차 흐름을 손으로 재현 |
| M4 | 수익화 화면(가짜 결제·가짜 광고) + 출석판·미션·업적 | 지급 → 저장 → 확정 순서, 재실행 복구 |
| M5 | AGSL 후처리(충격파·화면 금)·장식 붕괴·이벤트 6종 | 13+ 실기기, 2ms 예산 |
| M6 | 실제 SDK(AdMob·Billing 테스트 트랙)·원격 설정·분석 | 내부 테스트 트랙 배포 |

## 6. 성능 · 품질 예산

- 판 계산(스와이프 1회) ≤ 2ms. **JVM 실측(11-23, `gradle -p core run --args=bench`)**: 스와이프 p50 34µs·p99 166µs, 연출 계획 p50 1µs, 저장 인코딩 p50 23µs·p99 83µs. 폰(대략 5~10배 느림)에서도 예산 안. BigInteger는 표시 직전에만 문자열.
- 프레임 16ms 중 FX ≤ 2ms(Pixel 6a), 저사양 자동 강등.
- 저장 ≤ 20KB JSON, 스와이프마다 비동기 원자적 쓰기(DataStore).
