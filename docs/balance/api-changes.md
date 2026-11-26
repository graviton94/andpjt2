# 화면 계약

## 오프라인 보너스

`bm()`과 `snapshot`에 같은 `offlineBonus` 객체를 추가했다. `bm().state`와 다른 응답의 상태 스냅샷에서도 읽을 수 있다.

| 필드 | 단위와 의미 |
|---|---|
| `offlineBonus.rewardBonusPermille` | 가산분 천분율. `700`은 광고 시청 또는 패스 공통 +70% |
| `offlineBonus.ultraBonusPermille` | 가산분 천분율. `500`은 울트라 추가 +50% |
| `offlineBonus.ultraWithPassMultiplierPermille` | 울트라 가입 시 총배율. 활성 구독이 없어도 상품 카드에 표시할 수 있다. |
| `offlineBonus.adActive`, `adLeftMs` | 광고 시청 유효 여부와 남은 밀리초. UTC 기준 시청일과 다음날에 유효 |
| `offlineBonus.passActive`, `passLeftMs` | 패스 공통 혜택 유효 여부와 남은 밀리초. 터보와 울트라 포함 |
| `offlineBonus.ultraActive`, `ultraLeftMs` | 울트라 추가 혜택 유효 여부와 남은 밀리초 |
| `offlineBonus.rewardActive` | 광고 시청 또는 패스 중 하나 이상 유효 |
| `offlineBonus.multiplierPermille` | 현재 정산 총배율. 기본 `1000`, 공통 혜택 적용 시 `1700`, 울트라와 중첩 시 `2550` |

광고 기록과 패스가 둘 다 유효하면 공통 +70%를 한 번 적용한다. 울트라는 여기에 1.5를 곱한다. 총배율은 2.55배이고 기본 정산 대비 +155%다. 광고 버튼은 패스 공통 혜택과 남은 시간, 울트라 카드는 울트라 추가 혜택과 중첩값에 이 필드를 사용한다.

`offline(awayMs, now)` 응답에는 정산 시점의 `offlineBonus`와 `basePaid`, `bonusPaid`, `bonusPaidShort`를 추가했다. `basePaid` 및 `bonusPaid`는 `paid`와 같은 10진 문자열이다. `basePaid`는 동일 속도와 레벨 상한에서 광고·패스·울트라 가산분 없이 받을 값이다. `bonusPaid`는 정산에 실제 더해진 P이며, 상한에 걸리면 보너스가 활성 상태여도 0일 수 있다. 복귀 정산 화면에서는 `paidShort`와 `bonusPaidShort`를 사용한다.

## 기존 화면 및 저장 필드

`WebSession`의 `panel()`, `bm()`, 스냅샷, 스와이프 비트의 필드 이름과 타입, 단위는 기존과 같다. 화면에서 쓰는 상품 가격·지급량·효율의 데이터 소스를 `RuleSet.commerce`로 옮겼다. 월간·터보·울트라의 일일 데이터와 오버클럭은 앱 진입 및 구매에서도 정산한다. 스타터 카운트다운은 첫 세션 시작 시점을 보존한다. 저장은 기존 버전 1을 사용하고 기존 계정 필드를 그대로 읽는다.

디버그 APK에서 WebView CDP와 가짜 결제·광고 영수증을 활성화한다. 검증 스크립트는 서비스 화면의 버튼과 게임 브리지에 연결한다.

저장 내부의 광고 지급 번호는 버전 1에 선택 필드로 추가했다. 기존 `fakead-` 지급 번호는 첫 로드 때 최근 기록만 광고 원장으로 옮긴다. 결제 및 출처를 분류할 수 없는 기존 번호는 구입 원장에 남는다.

## 무한 성장 후보의 큰 수 계약

화면은 `…Short` 값을 그대로 표시한다. 정확값이 필요한 저장·공유용 데이터는 십진 문자열로 받는다. 사용 가능 여부와 기록 경신은 코어가 정확 정수끼리 비교해 불리언으로 내보낸다. `…Short`가 생기기 전의 화면은 기존 문자열을 소수로 바꾸지 않고 앞 세 자리와 문자열 길이로 표시할 수 있다.

| 응답 | 표시할 문자열 | 코어 판정 |
|---|---|---|
| `snapshot` | `walletShort`, `runEarnedShort`, `bestSwipeShort`, `filesShort`, `backupFilesNowShort`, `gbShort`, `cloudGbNowShort` | `canReset`, `canSync` |
| `snapshot.deckApps[]`, `snapshot.shopApps[]` | `upgradePriceShort`, `priceShort` | `canUpgrade`, `canBuy` |
| `panel.apps[]`, `panel.backupShop[]`, `panel.cloudShop[]` | `priceShort`, `price1Short`, `price10Short` | `canBuy`, `canUpgrade`, `canUpgrade10` |
| `panel()` | `nextLevelPShort`, `filesShort`, `gbShort` | 이미 계산된 숫자형 `levelProgress` |
| 스와이프와 비트 | `earnedShort`, `cappedShort`, `chipsShort`, `lostShort` | `newBestSwipe` |
| 복귀 정산 | `paidShort`, `basePaidShort`, `bonusPaidShort` | 실제 인정 시간과 EX 추가 정산은 아래 필드를 사용 |

기존 숫자 필드는 호환 범위를 유지하고, 파일·GB·그 가격처럼 이전에 숫자형이었던 필드의 정확값은 `filesExact`, `gbExact`, `priceExact`처럼 별도로 담는다. 화면은 이 값들을 문자열로 읽는다. 표기는 3유효숫자를 내림하며 기존 K·M·B·T·aa…zz를 사용한다. zz 뒤의 표기는 `1.23e+2043`처럼 십진 지수 문자열이다.

```json
{
  "walletShort": "1.23cn",
  "backupFilesNowShort": "2.4ab",
  "gbShort": "5.1M",
  "canReset": true,
  "canSync": false,
  "deckApps": [{"id": "memo", "upgradePriceShort": "1.24cn", "canUpgrade": false}]
}
```

### EX 상품 적용 후 추가할 계약

스와이프는 `exExtraP`와 `exExtraPShort`, 복귀는 `exExtraP`, `exExtraPShort`, `baseEligibleMs`, `eligibleMs`, `exExtraEligibleMs`를 낸다. 환생은 `paidFilesExact`, `paidFilesShort`, `exExtraFilesExact`, `exExtraFilesShort`를, GB 동기화는 `paidGbExact`, `paidGbShort`, `exExtraGbExact`, `exExtraGbShort`를 낸다. `exExtra…`는 동일 행동·상태에서 EX 없이 지급할 양과의 차이다. 이 수치에 이후의 복리 효과는 포함하지 않는다.

```json
{
  "earned": "1280000",
  "earnedShort": "1.28M",
  "exExtraP": "280000",
  "exExtraPShort": "280K",
  "newBestSwipe": true
}
```

```json
{
  "paidFilesExact": "1840000",
  "paidFilesShort": "1.84M",
  "exExtraFilesExact": "340000",
  "exExtraFilesShort": "340K"
}
```

위 큰 수 계약은 검증 후보용이다. 사용자가 선택한 B안의 EX 계정 연동은 운영 이식 승인 뒤 진행한다. [B안 검증 결과](B-CALIBRATION-REPORT.md)

## B안 복귀 표시 후보

`offline(awayMs, now)`의 `baseOfflineCapMs`, `offlineCapMs`, `exExtraOfflineCapMs`는 복귀 직전 상태에서 계산한 기본 상한, B 포함 상한, 둘의 차이다. `baseEligibleMs`와 `eligibleMs`는 실제 부재와 상한의 최솟값이며, `exExtraEligibleMs`는 이번 복귀에 추가로 인정된 시간이다. `exExtraPShort`는 정수 정산과 회차 지급 상한을 거친 실제 추가 지급의 표시 문자열이다.

```json
{
  "baseOfflineCapMs": 43200000,
  "offlineCapMs": 52020000,
  "exExtraOfflineCapMs": 8820000,
  "baseEligibleMs": 43200000,
  "eligibleMs": 52020000,
  "exExtraEligibleMs": 8820000,
  "exExtraPShort": "1.76M"
}
```

15시간 부재의 시험 응답이다. 화면 후보는 `EX 정산 상한 +147분`과 `이번 추가 인정 147분 · +1.76M P`를 표시했다. 6시간 부재는 같은 상한과 `이번 추가 인정 0분 · +0 P`였다. 기존 화면과 후보의 복귀 핸들러를 동일 응답으로 Chromium에서 비교했다. [화면 패치](../../tools/economy-sim/growth-loop/offline-display.patch) · [실측 결과](b-calibration-data/screen/rendered.json)

## 동기화 가속 검증 후보

`offline().awayMs`는 호스트가 전달한 실제 부재 시간의 밀리초다. 하한은 0이다. `paid`·`basePaid`·`bonusPaid`와 각 `Short` 짝, `offlineCapMs`, `baseOfflineCapMs`, `exExtraEligibleMs`, `exExtraPShort`, `offlineBonus`의 의미와 단위는 그대로 사용한다. 아래 15시간 복귀에서는 상한 14시간 27분을 빼면 초과 33분이다.

`basePaid`는 광고·패스 등의 추가 배율을 적용하기 전 지급액이며 EX는 그 안에 이미 반영돼 있다. `exExtraPShort`는 기존 시간 창 안의 효과와 시간 연장의 효과를 합친 비교값이다. 카드에서 `basePaid`와 `exExtraPShort`를 합치면 EX가 중복된다. 짧은 부재에서는 `exExtraEligibleMs`가 0이어도 `exExtraPShort`가 양수일 수 있다.

```json
{
  "awayMs": 54000000,
  "baseOfflineCapMs": 43200000,
  "offlineCapMs": 52020000,
  "exExtraEligibleMs": 8820000
}
```

수입을 이어받는 동안 `snapshot().carry`와 `panel().state.carry`에 같은 객체를 보낸다. `panel().carry`에서도 이 값을 읽을 수 있다. `ratio`는 채택 후보의 최초 계승 비율(0~1), `untilLevel`은 가산이 끝나는 레벨, `pPerSec`와 `pPerSecShort`는 현재 감쇄까지 반영한 초당 기준 수입의 정확값과 표시값이다. 실제 지급은 유효 스와이프, 활동 시간과 남은 P 상한을 같이 적용한다. 가산이 끝났거나 상품화하지 않은 규칙에서 `carry`는 생략한다.

```json
{
  "carry": {"ratio": 0.65, "untilLevel": 60, "pPerSec": "1240000", "pPerSecShort": "1.24M"}
}
```
