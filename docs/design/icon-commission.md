# 앱 아이콘 발주서 · 패러디 아이콘 10종

알림 청소부의 알림 앱 60종 중 50종은 코드(SVG)로 그린다(`tools/icons/build_icons.py` → `docs/design/app-icons/`). 아래 10종은 마스코트·일러스트가 정체성이거나(7종), 코드로는 실제 안드로이드 아이콘과 싱크로가 낮아(3종: 메롱·글린츠츠·잡스트리트) 이미지로 발주한다. 납품 전까지는 점선 테두리 자리표시 아이콘이 쓰인다.

## 0. 원칙

- **이모지와 기호 글꼴 문자는 절대 쓰지 않는다.** 기기마다 이모지로 바뀌어 보인다. 모든 픽토그램은 직접 그린 SVG(저장소 자체 제작, CC0)이고, 외부 픽토그램은 **CC0만** 쓴다.
- **헥스값과 스타일은 실제 아이콘에 최대한 가깝게**: 넷플릭스 리본 음영, 인스타그램 방사형 그라데이션, 텔레그램·틴더·라자다·쇼피·위챗·토스 그라데이션, 구글 계열 4색 분할, 페이팔 겹친 P 등. 비틀기(이름 말장난)는 모양에 한 번만 넣는다.
- 발주 7종의 배경색도 원작에 맞춘다: 챗GTP #000000, 로빈훗훗 #0B0B0B(전경 네온 #CCFF00 또는 #00C805), 컵컷 #000000, 트래블로까 #0194F3, 래딧 #FF4500, 듀오링구 #58CC02, 마마고 #03C75A.

## 1. 구분 기준

| 구분 | 기준 | 예 | 수 |
|---|---|---|---|
| 코드 · 픽토그램 | 기본 앱처럼 픽토그램 자체가 정본 | 전화 수화기, 메시지 말풍선, 시계 | 12 |
| 코드 · 글자 마크 | 1~7글자와 브랜드풍 색. 글자는 OFL 폰트(Do Hyeon·Nunito·Inter)에서 뽑은 윤곽선 | 테이크북 t, 나베르 N, 배다민, kubbang, 링크드아웃 out | 17 |
| 코드 · 단순 도형 | 원·말풍선·삼각형 등 몇 개 도형 | 너튜브, 스팟티파이 호, 텔레그림 비행기, 틱특 어긋난 음표 | 21 |
| **발주** | 마스코트·캐릭터·복잡한 기하 마크, 또는 코드 싱크로가 낮은 것 | 아래 10종 | **10** |

## 2. 공통 사양 (모든 프롬프트 앞에 붙인다)

- 1024 × 1024 PNG 두 장: **전경**(투명 배경), **배경**(단색 또는 2색 그라데이션). 안드로이드 적응형 아이콘용.
- 전경은 가운데 **지름 66% 원 안**(안전 영역)에 들어가야 한다. 모서리는 앱이 둥근 사각형으로 자른다.
- 평면 벡터 스타일, 굵고 단순한 형태, 그림자·질감·글자 없음(지정한 경우만 글자). 48px로 줄여도 알아볼 것.
- **패러디 원칙**: 원작을 바로 떠올리게 하되 똑같지 않게. 실루엣·색 계열은 따르고, 이름 말장난에 맞는 **비틀기 1~2개**를 반드시 넣는다. 원작 로고를 이미지 생성 입력으로 넣지 않는다.
- 납품: `docs/design/app-icons/<id>-fg.png`, `<id>-bg.png`, 가능하면 SVG도.

## 3. 프롬프트

### 3.1 마마고 (`papago`) · 3티어 · 아시아권 · 업무 · 번역 앱 패러디

> Flat vector app icon foreground, transparent background. A cheerful round green parrot head in profile, white face patch, chunky yellow beak, **wearing a tiny pink bow on its head (it is the mom: "마마")**, a small speech bubble with two different scripts (a simple "가" and "A") floating beside the beak. Bold simple shapes, no outlines, no gradients, centered within a circle of 66% width. Colour palette: #03C75A green, #FFFFFF, #FFD43B beak, #FF8FB1 bow.
> Background layer: solid #E9FBEF.

### 3.2 듀오링구 (`duo`) · 3티어 · 서양권 · 업무 · 언어 학습 앱 패러디

> Flat vector app icon foreground, transparent background. A round, bright lime-green owl mascot seen from the front, big white eyes with black pupils looking slightly sideways, small orange beak and feet, **holding a tiny alarm clock under one wing with a sweat drop (the nagging-reminder joke)**. Chubby egg-shaped body, two small tufts on the head, no text. Bold flat shapes, no gradients, centered within a circle of 66% width. Palette: #58CC02, #89E219 belly, #FFFFFF, #4B4B4B pupils, #FF9600 beak.
> Background layer: solid #FFFFFF.

### 3.3 컵컷 (`edit`) · 5티어 · 아시아권 · 미디어 · 영상 편집 앱 패러디

> Flat vector app icon foreground, transparent background. A bold geometric mark made of two thick white strokes that cross like an open pair of scissors forming a stylised "X", **with the lower ends shaped like the rim of a paper cup (the "cup" pun)**. Strictly geometric, rounded stroke ends, monochrome white, centered within a circle of 66% width, no text.
> Background layer: solid #111111.

### 3.4 트래블로까 (`travel`) · 5티어 · 동남아권 · 생활 · 여행 예약 앱 패러디

> Flat vector app icon foreground, transparent background. A minimal stylised bird made of three overlapping rounded shapes in flight, forming a soft arrow pointing up-right, **with a small suitcase hanging from its feet**. Colours: sky blue #0194F3 body, warm orange #FF8A00 wing accent, white suitcase with blue handle. Bold flat shapes, no outlines, centered within a circle of 66% width, no text.
> Background layer: solid #FFFFFF.

### 3.5 래딧 (`reddit`) · 5티어 · 서양권 · 소통 · 커뮤니티 앱 패러디

> Flat vector app icon foreground, transparent background. A friendly white alien head, wide oval face, two round eyes, simple smile, a single antenna ending in a ball, **wearing round reading glasses and holding a tiny upvote arrow like a flag**. Thick shapes, black #1A1A1B eyes and outlines kept minimal, centered within a circle of 66% width, no text.
> Background layer: solid #FF4500 orange-red.

### 3.6 로빈훗훗 (`robin`) · 7티어 · 서양권 · 금융 · 주식 거래 앱 패러디

> Flat vector app icon foreground, transparent background. A single bold feather drawn as a sharp geometric shape, **bent into a rising stock-chart zigzag at its tip**, plus a tiny archer's hat outline above it (the hood pun). Monochrome neon green #00C805 on transparent, crisp angular shapes, centered within a circle of 66% width, no text.
> Background layer: solid #0B0B0B.

### 3.7 챗GTP (`ai`) · 10티어 · 글로벌 · 업무 · AI 챗봇 앱 패러디

> Flat vector app icon foreground, transparent background. A symmetrical knot of six rounded interlocking loops forming a hexagonal flower, white strokes of even thickness, **with one loop deliberately tied in a small bow (the typo "GTP" — slightly off)**. Clean geometric construction, rounded joins, centered within a circle of 66% width, no text.
> Background layer: solid #10A37F teal.

### 3.8 메롱 (`music`) · 3티어 · 아시아권 · 미디어 · 국내 음원 앱 패러디

> Flat vector app icon foreground, transparent background. A white rounded melon-fruit silhouette with a small two-leaf sprout on top, matching the real Korean music app's simple white-on-green mark in proportions, **with a little cartoon tongue poking out of the fruit's side ("메롱" = sticking one's tongue out)**. No text, bold flat shapes, centered within a circle of 66% width. Palette: #FFFFFF fruit, #00CD3C cut-outs, #FF6B8B tongue.
> Background layer: solid #00CD3C.
> 참고: 원작 앱 아이콘의 비율·여백을 따라 그리되 똑같이 따라 그리지는 않는다.

### 3.9 글린츠츠 (`glints`) · 4티어 · 동남아권 · 업무 · 구직 앱 패러디

> Flat vector app icon foreground, transparent background. A bold rounded lowercase "g" mark in the style of the real Southeast-Asian job app's logo, **with a small four-point sparkle ("glint") doubled at its top right (the "츠츠" repeat pun)**. Match the original app icon's brand colour family (designer checks the current Play Store icon); default coral #FF5C35 on white if unclear. Outlined lettering, no other text, centered within a circle of 66% width.
> Background layer: solid #FFFFFF (or the original's background colour).

### 3.10 잡스트리트 (`jobs`) · 7티어 · 동남아권 · 업무 · 구직 앱 패러디

> Flat vector app icon foreground, transparent background. A bold white letterform "J" whose descender turns into a small street with a dashed centre line, **with a tiny briefcase standing at the end of the street**. Match the real Southeast-Asian job app's brand colours (designer checks the current Play Store icon); default navy #0D3880 background with white and a #E60278 accent. Centered within a circle of 66% width, no other text.
> Background layer: solid brand colour (default #0D3880).

## 4. 검수 체크리스트

- [ ] 48px로 줄였을 때 실루엣으로 무슨 앱인지 알아본다.
- [ ] 원작 로고와 나란히 놓았을 때 비틀기가 한눈에 보인다(똑같지 않다).
- [ ] 전경이 안전 영역 원 안에 있다. 배경과 전경이 분리돼 있다.
- [ ] 라이트(#EEF1F7)·다크(#16130F) 패널 위 모두에서 윤곽이 산다.
- [ ] 글자를 넣지 않았다(지정한 경우 제외).
