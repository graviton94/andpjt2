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
