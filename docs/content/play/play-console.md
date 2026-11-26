# Play Console 등록 답안 · Do Not Disturb! (방해 금지!)

콘솔 화면 순서 그대로. 기준 앱 사실(11-26, 실제 로그인·결제 기준): WebView 게임 + Kotlin 셸, **게스트로 전부 플레이 가능 + 선택 로그인(Google·카카오 → Firebase Auth)**, 로그인 시 Firestore 진행 백업, Firebase Analytics·Crashlytics·Remote Config, AdMob **보상형만**(UMP 동의: 유럽·미국 주), Play Billing(소모품·1회·구독 3종, 구매 토큰 서버 확인), **사용자 입력 텍스트·채팅 없음**, **확률형 상품 없음**. 개인정보처리방침·약관은 이 기준으로 갱신해 공개함(웹 PR #27).

## 0. 정해 둘 것

| 항목 | 값 |
|---|---|
| 패키지명 | `io.github.graviton94.donotdisturb` (다른 앱과 같은 `io.github.graviton94.<앱>` 규칙, 첫 AAB 업로드 뒤 영구 고정) |
| 기본 언어 | English (United States) en-US, 번역 추가: 한국어 ko-KR · 일본어 ja-JP · 번체 중국어 zh-TW · 스페인어 es-419(+es-ES) — 검토 `docs/content/l10n/launch-languages.md` |
| 웹 (GitHub Pages) | **작성 완료(graviton94.github.io 저장소 `ccr-9efafd3c-bh5fdz` 브랜치, main에 합쳐야 공개)** — 소개 `/donotdisturb/` · 개인정보처리방침 `/donotdisturb/privacy/` · 데이터 삭제 `/donotdisturb/privacy/#delete` · 이용약관 `/donotdisturb/terms/` · 루트 `app-ads.txt` |
| 문의 | 페이지는 사이트의 개발자 소개(`/about/`)로 연결. Play 등록용 공개 이메일은 출시 전 확정 |
| AAB 서명 | CI 업로드 키(비밀값), versionCode = 1000 + 실행 번호 |

## 1. 앱 만들기

이름 `Do Not Disturb!: Merge Idle` (ko: `방해 금지!: 방치형 클리커`) · 게임 · 무료 · 정책·수출 법규 동의.

## 2. 앱 콘텐츠

| 화면 | 답 |
|---|---|
| 개인정보처리방침 | `https://graviton94.github.io/donotdisturb/privacy/` |
| 앱 액세스 권한 | **모든 기능 제한 없이 사용 가능** — 로그인은 선택(진행 백업용)이고 게스트로 모든 기능을 쓸 수 있음. 설명란: "Sign-in is optional (Google or Kakao) and only backs up progress; every feature is available as a guest." |
| 광고 | 예, 광고 포함 — **보상형 광고만**, 사용자가 버튼을 눌러 직접 시청, 강제 전면·배너 없음 |
| 콘텐츠 등급 | 2-1 |
| 타깃층 | **13세 이상** (13~15, 16~17, 18+). 귀여운 아이콘 → "어린이에게 관심을 끌 수 있음" 예, 설명: "폰 알림을 패러디한 정리 퍼즐, 아동 대상 아님, 결제·광고 포함" |
| 뉴스·정부·금융 기능·건강 | 모두 아니요 (게임 속 '금융' 카테고리는 패러디 앱 분류일 뿐 실제 금융 기능 없음) |
| 데이터 보안 | 2-2 CSV |
| 광고 ID | 예 → 광고 또는 마케팅, 애널리틱스 |
| 계정 삭제 | 앱에서 계정 생성 가능 → **예**. 앱 안: 설정 → 계정 → 계정 삭제. 웹: `https://graviton94.github.io/donotdisturb/privacy/#delete` |

### 2-1. IARC

- 카테고리: 게임 (캐주얼/퍼즐)
- 폭력·공포·성적·욕설·약물·술·담배·실제 돈 도박·모의 도박: 모두 **아니요**
  - 게임 속 '행운 알림' 카드(옛 이름 로또 알림)는 **게임 재화(P)로만** 결과가 정해지고 돈을 걸거나 돈으로 살 수 없음 → 모의 도박 아님. 이름도 도박을 떠올리지 않게 바꿨다(11-23)
- 사용자 간 상호작용 / 콘텐츠 공유: **아니요** (채팅·친구·이름 입력 없음. 일일 도전 순위는 출시 후 추가 시 다시 답함)
- 위치 공유: 아니요
- 디지털 상품 구매: **예**
- 무작위 아이템 구매: **아니요** (확률형 없음, 모든 상품은 사기 전 내용 공개)
- 예상 결과: 전체이용가 / PEGI 3 / ESRB Everyone (+ 인앱 구매)

### 2-2. 데이터 보안 — CSV

1. `python3 make_data_safety_csv.py profile_donotdisturb.json data_safety_import.csv` (이 폴더, 이미 생성됨)
2. 콘솔 → 데이터 보안 → 가져오기·내보내기 → **CSV 가져오기** → 미리보기 → 저장

판단 근거:
- 계정 생성 방법: **OAuth** (`PSL_ACM_OAUTH`: Google·카카오). 계정 삭제 URL = 데이터 삭제 URL(`#delete`).
- CSV 가져오기 뒤 콘솔에서 직접 답할 것: "외부 계정으로 로그인하나요?" → 예(Google·카카오). 독립 보안 검토 → 아니요.
- 수집(로그인한 경우만, **선택**): 이름(표시 이름) · 이메일 · 사용자 ID · 기타 앱 활동(게임 진행 백업) → 앱 기능 · 계정 관리.
- 수집(필수): 대략적 위치(광고 SDK) · 구매 내역(지급 확인 · 부정 방지 · 분석) · 진단 · 비정상 종료 로그 · 앱 상호작용 · 기기 ID(광고 ID).
- 공유 ✔ 는 **AdMob으로 가는 항목만**: 위치 · 진단 · 상호작용 · 기기 ID. Firebase·Google Cloud는 우리 대신 처리하는 서비스 제공업체라 공유가 아님.
- 임시 처리 아님, 전송 암호화 예, 삭제 요청 가능 예.

## 3. 스토어 등록정보

문구: `docs/content/l10n/store-listing.md` (한/영). 규격:

| 항목 | 메모 |
|---|---|
| 앱 아이콘 512×512 | 언어별 효과음 `docs/design/brand/icon-512-<언어>.png` (팡!/POP!/パン!/砰!/¡PUM!), 폭발 말풍선은 일부러 가장자리에서 잘림. 원본 `tools/brand/icon.html` + 발주 에셋 `ui-assets/comic-fx`, `node tools/brand/render.js` |
| 그래픽 이미지 1024×500 | `docs/design/brand/feature-<언어>.png` 5종 — 오른쪽 폰(상태표시줄 포함) 화면에서 터지는 장면 + 왼쪽 제목·동사 스티커 3개. 글꼴 Black Han Sans · Luckiest Guy · Dela Gothic One · Noto Sans TC (OFL). 중국어 글꼴은 `tools/brand/subset_tc.py`로 쓴 글자만 |
| 스크린샷 9:16 4~8장 | ① 9연쇄 정산 쾅 ② 화면 유리 깨짐 ③ 앱 60종 컬렉션 ④ 프리미엄 ★ ⑤ 30일 출석판 ⑥ 스팸 전화 차단. 언어별로 글자 섞이지 않게 |
| 카테고리 · 태그 | 게임 › 퍼즐(또는 캐주얼) · 태그: 증분 게임, 퍼즐, 캐주얼, 병합 퍼즐, 아케이드 |

**패러디 주의**: 스크린샷·설명에 실제 앱 이름·로고를 쓰지 않는다(영문 이름은 `l10n/en.json`). 실제 상표와 혼동될 문구 금지.

## 4. 인앱 상품 (결제 프로필 먼저)

| ID | 종류 | 이름 (ko / en) | 가격 |
|---|---|---|---|
| `data_100` | 일회성 소모 | 데이터 100 / 100 Data | ₩2,200 (기준가: 일부러 비효율, 모든 "N배 이득"의 기준) |
| `data_540` | 일회성 소모 | 데이터 540 (+10%) / 540 Data | ₩5,900 |
| `data_1200` | 일회성 소모 | 데이터 1,200 (+20%) / 1,200 Data | ₩12,000 |
| `data_3700` | 일회성 소모 | 데이터 3,700 (+35%) / 3,700 Data | ₩33,000 |
| `data_8600` | 일회성 소모 | 데이터 8,600 (+50%) / 8,600 Data | ₩69,000 |
| `data_16800` | 일회성 소모 | 데이터 16,800 (+70%) / 16,800 Data | ₩119,000 |
| `rookie_pass` | 일회성 비소모 | 초심자 패스 / Rookie Pass | ₩3,300 (설치 후 7일만 노출: 즉시 300 + 7일간 매일 50 + ★ 선택권 1) |
| `starter_pack` | 일회성 비소모 | 스타터 팩 / Starter Pack | ₩2,500 (설치 후 48시간 카운트다운: 데이터 500 + ★ 선택권 1 + 수입 부스트 3 + 오버클럭 3) |
| `growth_25` · `growth_60` · `growth_100` | 일회성 비소모 | 성장 지원 팩 / Growth Pack | ₩5,900~12,000 |
| `pass_monthly` | 정기 결제 (기본 요금제 월) | 월간 패스 / Monthly Pass | ₩5,500 |
| `pass_turbo` | 정기 결제 (월) | 터보 패스 / Turbo Pass | ₩33,000 |
| `pass_ultra` | 정기 결제 (월) | 울트라 패스 / Ultra Pass | ₩99,000 |

- 구독 3종은 **같은 구독 그룹**에 두고 업·다운그레이드 허용(앱은 `CHARGE_PRORATED_PRICE`, 21장).
- 첫 구매 2배는 콘솔이 아니라 앱 지급량으로: 데이터 상품 통틀어 **처음 1번만** 2배(보너스 최대 +1,200) → ₩12,000이 첫 구매 최고 효율. 이후 원래대로.
- 모든 결제는 앱에서 확인 팝업 후 결제 흐름 시작.
- 상품 이미지: `docs/design/ui-assets/shop/*.png`를 1:1 1024px PNG(글자 없음)로 다시 내보내 사용.
- 라이선스 테스트 계정 등록 후 내부 테스트.

## 5. 앱 서명

- 업로드 키·시험 키: `keystore/*.jks.gpg` (CI가 만든 키, 지문은 `docs/platform/firebase-admob-setup.md` 5절). Firebase·카카오에 등록 완료.
- **첫 AAB를 올린 뒤**: 설정 → 앱 무결성 → 앱 서명 키의 SHA-1·SHA-256을 Firebase(디지털 지문)와 카카오(키 해시 = SHA-1을 base64)에 추가. 스토어 설치본은 이 키로 서명된다.

## 6. AdMob

완료(11-26): 앱 등록 · Firebase 연결 · 보상형 단위 1개 `DD_Rewarded_All`(지면은 Analytics 이벤트 `ad_show/ad_reward`의 slot으로 구분) · ID는 비밀값 `ADMOB_APP_ID`/`ADMOB_REWARDED_ID` · GDPR·미국 주 메시지 게시. 남은 것: `app-ads.txt` 확인, 미디에이션(출시 후).

## 7. 비공개 테스트까지 체크리스트 (11-26)

| # | 화면 | 할 일 | 값 |
|---|---|---|---|
| 1 | 테스트 및 출시 › 내부 테스트 | 새 버전 만들기 → **첫 AAB 직접 업로드**(Actions › Android AAB (Play) 결과물) | 버전 0.1.0 |
| 2 | 앱 콘텐츠 | 개인정보처리방침 · 앱 액세스 · 광고 · 콘텐츠 등급 · 타깃층 · 데이터 보안 · 정부 앱 · 금융 · 건강 | 2절 답안 그대로 |
| 3 | 스토어 등록정보 | 앱 이름 · 간단한 설명 · 자세한 설명 · 아이콘 · 그래픽 이미지 · 폰 스크린샷 2장 이상 | 3절, `store-listing.md`, `docs/design/brand/` |
| 4 | 스토어 설정 | 카테고리 게임 › 캐주얼 · 연락처 이메일 · 웹사이트 `https://graviton94.github.io/donotdisturb/` | |
| 5 | 테스트 및 출시 › 비공개 테스트 | 트랙 만들기 → 테스터 이메일 목록(12명 이상) → 국가 → AAB(내부 버전 승격) → 검토 제출 | 개인 계정은 12명 × 14일 연속 후 프로덕션 신청 가능 |
| 6 | 앱 무결성 | 앱 서명 키 SHA → Firebase·카카오 | 5절 |
| 7 | 수익 창출 | 결제 프로필 확인 → 인앱 상품·구독 등록 (결제 권한 있는 AAB가 올라간 뒤 열림) | 4절 |

## 8. 테스트 트랙

내부 테스트 → **비공개 테스트 12명 × 14일 연속** → 프로덕션 신청. 14일 동안 볼 지표: D1·D7, 첫 초기화 도달(목표 2일), 첫 결제까지 일수, 광고 시청 분포, 크래시·ANR, 결제 퍼널(`iap_view → iap_start → iap_success`), 프로필별 Lv124 속도(17장 시뮬과 비교).
