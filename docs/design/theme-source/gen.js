const fs = require('fs');
const path = require('path');
const font = path.resolve('node_modules/pretendard/dist/public/variable/PretendardVariable.ttf');

const I = { // 24px stroke icons
  wifi: '<path d="M2 9a15 15 0 0 1 20 0M5 12.5a10 10 0 0 1 14 0M8.5 16a5 5 0 0 1 7 0"/><circle cx="12" cy="19.3" r="1.2" fill="currentColor"/>',
  bt: '<path d="M7 7l10 10-5 5V2l5 5L7 17"/>',
  bolt: '<path d="M13 2L4 14h7l-1 8 9-12h-7z" fill="currentColor" stroke="none"/>',
  auto: '<path d="M20 11a8 8 0 0 0-14.5-4.5M4 13a8 8 0 0 0 14.5 4.5"/><path d="M5 3v4h4M19 21v-4h-4"/>',
  ad: '<rect x="3" y="5" width="18" height="14" rx="3"/><path d="M10 9.5v5l4.5-2.5z" fill="currentColor"/>',
  siren: '<path d="M6 18v-5a6 6 0 0 1 12 0v5M4 18h16v3H4zM12 2v2M4.2 5.2l1.4 1.4M19.8 5.2l-1.4 1.4"/>',
  bag: '<path d="M5 8h14l-1 13H6zM9 8V6a3 3 0 0 1 6 0v2"/>',
  up: '<path d="M12 20V5M6 11l6-6 6 6"/><path d="M5 21h14"/>',
  note: '<path d="M9 18V5l11-2v13"/><circle cx="6.5" cy="18" r="2.5"/><circle cx="17.5" cy="16" r="2.5"/>',
  sfx: '<path d="M4 9v6h4l5 4V5L8 9z"/><path d="M16.5 8.5a5 5 0 0 1 0 7M19 6a8.5 8.5 0 0 1 0 12"/>',
  vib: '<rect x="8" y="3" width="8" height="18" rx="2"/><path d="M4 8v8M20 8v8M1.5 10v4M22.5 10v4"/>',
  motion: '<circle cx="12" cy="12" r="8"/><path d="M8 12h8"/>',
  moon: '<path d="M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z" fill="currentColor" stroke="none"/>',
  trophy: '<path d="M8 4h8v5a4 4 0 0 1-8 0zM8 6H4a3 3 0 0 0 4 4M16 6h4a3 3 0 0 1-4 4M12 13v4M8 21h8M9 17h6"/>',
  gear: '<path stroke="none" fill="currentColor" d="M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58a.49.49 0 0 0 .12-.61l-1.92-3.32a.49.49 0 0 0-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54a.48.48 0 0 0-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96a.48.48 0 0 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58a.49.49 0 0 0-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6a3.6 3.6 0 1 1 0-7.2 3.6 3.6 0 0 1 0 7.2z"/>',
  power: '<path d="M12 3v9M6.3 6.3a8 8 0 1 0 11.4 0"/>',
  pencil: '<path d="M4 20l1-5L16 4l4 4L9 19zM14 6l4 4"/>',
  eye: '<path d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/>',
  cal: '<rect x="3" y="5" width="18" height="16" rx="3"/><path d="M3 10h18M8 3v4M16 3v4"/>',
  help: '<circle cx="12" cy="12" r="9"/><path d="M9.5 9a2.5 2.5 0 1 1 3.5 2.3c-.7.3-1 .9-1 1.7M12 17h.01"/>',
  play: '<path d="M7 4l13 8-13 8z" fill="currentColor" stroke="none"/>',
  sparkle: '<path d="M12 2l2.2 6.8L21 11l-6.8 2.2L12 20l-2.2-6.8L3 11l6.8-2.2z" fill="currentColor" stroke="none"/>',
  chev: '<path d="M6 9l6 6 6-6"/>',
};
const ic = (n, s = 22) => `<svg width="${s}" height="${s}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${I[n]}</svg>`;

const themes = [
  { id: 'amber', name: '시안 A · 앰버 나이트', tag: '따뜻한 다크 · 오렌지 포인트',
    desc: '레퍼런스 스크린샷과 가장 가까운 안. 차분한 숯빛 배경에 켜진 상태만 오렌지로 빛나서, 게임에서 “켜진 것 = 보상이 붙은 것”이 바로 읽힌다.',
    bg: 'radial-gradient(120% 60% at 30% 0%, #3a3633 0%, #211f1e 55%, #181716 100%)', surface: 'rgba(255,255,255,.055)', surface2: 'rgba(255,255,255,.10)',
    tileOff: '#5a5653', accent: '#ee9a3f', onAccent: '#fff', text: '#f4f1ee', sub: '#a9a39e', card: 'rgba(255,255,255,.07)', cardLine: 'rgba(255,255,255,.06)',
    hot: '#ffcf6b', danger: '#ff6b4a', board: '#141312', boardText: '#f4f1ee' },
  { id: 'mint', name: '시안 B · 민트 글래스', tag: '차가운 다크 · 유리 질감 · 민트 포인트',
    desc: '심야의 잠금화면 느낌. 반투명 유리 카드 뒤로 색 번짐이 비치고, 민트와 시안이 합치기·연쇄의 에너지를 맡는다. 스튜디오 전작(Mint & Friends)과 색 계보가 이어진다.',
    bg: 'radial-gradient(70% 40% at 85% 12%, rgba(64,224,190,.35), transparent 70%), radial-gradient(60% 35% at 5% 70%, rgba(80,110,255,.30), transparent 70%), linear-gradient(180deg,#0c1420,#0a1018)',
    surface: 'rgba(255,255,255,.06)', surface2: 'rgba(255,255,255,.12)', tileOff: 'rgba(255,255,255,.14)', accent: '#4fe0bd', onAccent: '#05231c',
    text: '#eef6ff', sub: '#93a4b8', card: 'rgba(255,255,255,.08)', cardLine: 'rgba(255,255,255,.10)', hot: '#9ff7ff', danger: '#ff5f8a', board: '#070c12', boardText: '#eef6ff', glass: true },
  { id: 'day', name: '시안 C · 데이라이트', tag: '밝은 라이트 · 블루 포인트',
    desc: '낮 시간의 순정 안드로이드. 흰 유리 위에 블루 포인트. 밝은 바탕이라 폭발 순간의 섬광·균열을 어두운 오버레이로 반전시켜 대비를 만든다. 다크 모드 시안과 짝으로 쓸 수 있다.',
    bg: 'radial-gradient(90% 50% at 20% 0%, #e3ecff 0%, #f3f5fa 55%, #eef0f5 100%)', surface: 'rgba(20,30,60,.05)', surface2: 'rgba(20,30,60,.09)',
    tileOff: '#d9dde6', accent: '#3867f0', onAccent: '#fff', text: '#16181d', sub: '#646b78', card: '#ffffff', cardLine: 'rgba(20,30,60,.06)', hot: '#ff8a1f', danger: '#ef4444', board: '#e6e9f0', boardText: '#16181d', light: true },
];

const apps = [
  { n: '톡탁톡', c: '#7c5cff', g: '톡', msg: '조용한 단톡방에 메시지가 쌓였어요', t: '지금', cnt: 12, th: 16 },
  { n: '메일소포', c: '#3b9bff', g: '@', msg: '중요 표시한 메일이 더 왔어요', t: '1분 전', cnt: 6, th: 16 },
  { n: '문앞왔', c: '#2fbf71', g: '◆', msg: '도착 2분 전이라고 한 지 8분 됐어요', t: '2분 전', cnt: 4, th: 16, swipe: true },
  { n: '메일소포', c: '#3b9bff', g: '@', msg: '받은편지함에 새 메일', t: '3분 전', cnt: 9, th: 16, merge: true },
  { n: '스팸', c: '#ff5a4f', g: '!', msg: '광고 알림 · 두 번 더 밀면 복제', t: '4분 전', event: true },
  { n: '일정찍', c: '#f2a71b', g: '22', msg: '끝나는 시간이 또 미정이에요', t: '6분 전', cnt: 8, th: 16 },
  { n: '피드또봐', c: '#ff5fa2', g: '✿', msg: '이미 본 피드를 다시 추천합니다', t: '9분 전', cnt: 2, th: 16 },
];

function statusBar(t, clock = '10:24') {
  return `<div class="sb"><span class="clock">${clock}</span><span class="sbr">
    <span class="sbi hot">${ic('sparkle', 15)}</span>${ic('bt', 15)}${ic('wifi', 16)}
    <span class="sig"><i></i><i></i><i></i><i class="off"></i></span>
    <span class="bat"><b style="width:75%"></b><em>9/12</em></span></span></div>`;
}

function mainScreen(t) {
  const quick = [
    ['auto', '자동 정리', '3.2초', true], ['bolt', '과부하', '68%', 'ring'], ['ad', '2배 보상', '대기', false], ['siren', '알림훈련', '42초', false],
  ].map(([i, l, s, on]) => `<div class="q"><div class="qc ${on === true ? 'on' : ''} ${on === 'ring' ? 'ring' : ''}">${ic(i, 24)}</div><div class="ql">${l}</div><div class="qs">${s}</div></div>`).join('');
  const cards = apps.map(a => `<div class="card ${a.swipe ? 'swiping' : ''} ${a.merge ? 'merge' : ''} ${a.event ? 'event' : ''}">
      ${a.swipe ? `<div class="trail"></div>` : ''}
      <div class="ci" style="background:${a.c}">${a.g}</div>
      <div class="cb"><div class="ch"><b>${a.n}</b><span>${a.t}</span></div><div class="cm">${a.msg}</div>
        ${a.event ? '' : `<div class="bar"><i style="width:${Math.round(a.cnt / a.th * 100)}%"></i></div>`}</div>
      <div class="cn">${a.event ? '<span class="pill">돌발</span>' : `<b>${a.cnt}</b><span>/${a.th}</span>`}</div></div>`).join('');
  return `<div class="phone">${statusBar(t)}
    <div class="head"><div><div class="big">128.4K<small> P</small></div><div class="date">Lv 7 · 다음 레벨까지 32%</div></div>
      <div class="mult"><span>총 배율</span><b>×24.6</b></div></div>
    <div class="xp"><i style="width:68%"></i></div>
    <div class="quick">${quick}</div>
    <div class="secth"><b>알림 7개</b><span>새 묶음 폭발 기준 16</span></div>
    <div class="cards">${cards}
      <div class="combo">2연쇄!<small>메일소포 ×1.60 · +8.2K</small></div></div>
    <div class="pullhint">${ic('chev', 18)} 위에서 끌어내리면 상점·강화·설정</div>
    <div class="nav"><i></i></div></div>`;
}

function shadeScreen(t) {
  const grid = [['vib', '진동', true], ['motion', '움직임 줄이기', false], ['eye', '미리보기', true], ['moon', '다크 테마', true],
    ['trophy', '기록', false], ['cal', '일일 도전', false], ['ad', '광고 보상', false], ['help', '도움말', false]]
    .map(([i, l, on]) => `<div class="g"><div class="qc ${on ? 'on' : ''}">${ic(i, 24)}</div><div class="ql">${l}</div></div>`).join('');
  return `<div class="phone shade">${statusBar(t)}
    <div class="sh"><div class="shc">10:24<span>11월 23일 월요일</span></div><div class="shi">${ic('pencil')}${ic('power')}${ic('gear')}</div></div>
    <div class="pills">
      <div class="bp"><div class="qc on">${ic('bag', 24)}</div><div><b>앱 상점</b><span>비트서랍 · 450 P</span></div></div>
      <div class="bp"><div class="qc on">${ic('up', 24)}</div><div><b>앱 강화</b><span>3개 강화 가능</span></div></div></div>
    <div class="panel"><div class="grid">${grid}</div><div class="handle"></div></div>
    <div class="slider"><div class="track"><div class="fill" style="width:62%">${ic('note', 20)}</div><span class="sl">배경음</span></div><div class="qc on sm">${ic('note', 22)}</div></div>
    <div class="slider"><div class="track"><div class="fill" style="width:38%">${ic('sfx', 20)}</div><span class="sl">효과음</span></div><div class="qc on sm">${ic('vib', 22)}</div></div>
    <div class="media"><div class="mp">${ic('play', 20)}</div><div><b>Overload Shuffle</b><span>알림 청소부 OST · 2/6</span></div><div class="out">트랙 목록</div></div>
    <div class="pills">
      <div class="bp sm2"><div class="qc">${ic('trophy', 22)}</div><div><b>최고 기록</b><span>12연쇄 · 4.1M</span></div></div>
      <div class="bp sm2"><div class="qc">${ic('cal', 22)}</div><div><b>일일 도전</b><span>오늘 남은 1회</span></div></div></div>
    <div class="nav"><i></i></div></div>`;
}

function css(t) {
  return `@font-face{font-family:P;src:url('file://${font}')}
  *{box-sizing:border-box;margin:0;padding:0}
  body{font-family:P,sans-serif;background:${t.board};color:${t.boardText};width:1480px;padding:44px 52px;letter-spacing:-.01em}
  .bt{display:flex;justify-content:space-between;align-items:flex-end;margin-bottom:26px}
  .bt h1{font-size:44px;font-weight:800}.bt p{font-size:17px;opacity:.7;margin-top:6px;max-width:760px;line-height:1.5}
  .bt .tag{font-size:15px;color:${t.accent};font-weight:700;margin-bottom:8px}
  .row{display:flex;gap:36px;align-items:flex-start}
  .phone{width:412px;height:900px;border-radius:40px;overflow:hidden;position:relative;background:${t.bg};color:${t.text};
    box-shadow:0 30px 80px rgba(0,0,0,${t.light ? .18 : .5}),0 0 0 8px ${t.light ? '#cfd3db' : '#2a2a2a'};padding:0 18px}
  .cap{font-size:15px;font-weight:700;margin:16px 0 0 6px;opacity:.75}
  .sb{display:flex;justify-content:space-between;align-items:center;height:42px;padding:6px 8px 0;font-size:15px;font-weight:600}
  .sbr{display:flex;gap:7px;align-items:center}.sbi.hot{color:${t.hot}}
  .sig{display:flex;gap:2px;align-items:flex-end;height:13px}.sig i{width:3px;background:currentColor;border-radius:1px}
  .sig i:nth-child(1){height:5px}.sig i:nth-child(2){height:8px}.sig i:nth-child(3){height:11px}.sig i:nth-child(4){height:13px}.sig i.off{opacity:.3}
  .bat{position:relative;width:42px;height:19px;border-radius:10px;background:${t.surface2};overflow:hidden;display:flex;align-items:center;justify-content:center}
  .bat b{position:absolute;left:0;top:0;bottom:0;background:${t.accent};opacity:.9}.bat em{position:relative;font-style:normal;font-size:11px;font-weight:800;color:${t.light ? '#fff' : t.onAccent}}
  .head{display:flex;justify-content:space-between;align-items:flex-end;padding:14px 6px 8px}
  .big{font-size:44px;font-weight:700;letter-spacing:-.03em;line-height:1}.big small{font-size:20px;color:${t.sub};font-weight:600}
  .date{font-size:14px;color:${t.sub};margin-top:6px}
  .mult{text-align:right}.mult span{display:block;font-size:12px;color:${t.sub}}.mult b{font-size:24px;color:${t.accent}}
  .xp{height:4px;border-radius:2px;background:${t.surface2};margin:4px 6px 14px}.xp i{display:block;height:100%;border-radius:2px;background:${t.accent}}
  .quick{display:grid;grid-template-columns:repeat(4,1fr);background:${t.surface};border-radius:30px;padding:14px 6px 12px;${t.glass ? 'backdrop-filter:blur(20px);border:1px solid ' + t.cardLine : ''}}
  .q,.g{display:flex;flex-direction:column;align-items:center}
  .qc{width:58px;height:58px;border-radius:50%;display:flex;align-items:center;justify-content:center;background:${t.tileOff};color:${t.light ? '#3c4250' : '#e9e6e3'}}
  .qc.on{background:${t.accent};color:${t.onAccent};box-shadow:0 0 22px ${t.accent}55}
  .qc.ring{background:conic-gradient(${t.hot} 0 68%, ${t.tileOff} 68% 100%);color:${t.text};position:relative}
  .qc.ring svg{position:relative;z-index:1}.qc.ring::after{content:'';position:absolute;inset:5px;border-radius:50%;background:${t.light ? '#eef1f7' : '#2b2a2c'}}
  ${t.glass ? `.qc.ring::after{background:#132030}` : ''}
  .ql{font-size:12.5px;margin-top:7px;font-weight:600}.qs{font-size:11px;color:${t.sub};margin-top:1px}
  .secth{display:flex;justify-content:space-between;align-items:baseline;padding:18px 8px 8px}.secth b{font-size:15px}.secth span{font-size:12px;color:${t.sub}}
  .cards{display:flex;flex-direction:column;gap:6px;position:relative}
  .card{position:relative;display:flex;align-items:center;gap:12px;background:${t.card};border-radius:20px;padding:10px 14px 10px 12px;height:62px;
    border:1px solid ${t.cardLine};${t.glass ? 'backdrop-filter:blur(18px);' : ''}${t.light ? 'box-shadow:0 1px 2px rgba(20,30,60,.06);' : ''}}
  .ci{width:38px;height:38px;border-radius:12px;display:flex;align-items:center;justify-content:center;color:#fff;font-weight:800;font-size:15px;flex:none}
  .cb{flex:1;min-width:0}.ch{display:flex;gap:6px;align-items:baseline}.ch b{font-size:14px}.ch span{font-size:11.5px;color:${t.sub}}
  .cm{font-size:12.5px;color:${t.sub};white-space:nowrap;overflow:hidden;text-overflow:ellipsis;margin-top:1px}
  .bar{height:3px;border-radius:2px;background:${t.surface2};margin-top:6px;width:86%}.bar i{display:block;height:100%;border-radius:2px;background:${t.accent};opacity:.85}
  .cn{text-align:right;min-width:42px}.cn b{font-size:16px}.cn span{font-size:11px;color:${t.sub}}
  .pill{font-size:11px;font-weight:700;padding:4px 8px;border-radius:10px;background:${t.danger};color:#fff}
  .card.event{border-color:${t.danger}66;background:linear-gradient(90deg,${t.danger}22,${t.card})}
  .card.swiping{transform:translateX(78px) rotate(1.2deg);box-shadow:0 12px 30px rgba(0,0,0,.35);z-index:2;border-color:${t.accent}}
  .trail{position:absolute;right:100%;top:0;bottom:0;width:90px;margin-right:-6px;border-radius:20px 0 0 20px;
    background:linear-gradient(90deg,transparent,${t.accent}aa);display:flex;align-items:center;justify-content:flex-start;padding-left:2px}
  .trail span{display:flex;gap:4px;align-items:center;font-size:11px;font-weight:700;color:${t.onAccent};white-space:nowrap;transform:translateX(-64px);color:${t.accent}}
  .card.merge{box-shadow:0 0 0 2px ${t.hot},0 0 28px ${t.hot}88}
  .combo{position:absolute;right:4px;top:112px;transform:rotate(-6deg);font-size:40px;font-weight:900;color:${t.hot};
    text-shadow:0 3px 0 ${t.light ? '#a34d00' : '#000'},0 0 24px ${t.hot}aa;text-align:right;line-height:1;z-index:3}
  .combo small{display:block;font-size:13px;font-weight:700;color:${t.text};text-shadow:none;margin-top:6px;
    background:${t.light ? '#fff' : 'rgba(0,0,0,.55)'};padding:4px 8px;border-radius:8px}
  .pullhint{display:flex;gap:6px;align-items:center;justify-content:center;font-size:12px;color:${t.sub};margin-top:16px}
  .nav{position:absolute;bottom:10px;left:0;right:0;display:flex;justify-content:center}.nav i{width:120px;height:5px;border-radius:3px;background:${t.text};opacity:.5}
  .sh{display:flex;justify-content:space-between;align-items:center;padding:16px 8px 18px}
  .shc{font-size:40px;font-weight:700;letter-spacing:-.03em}.shc span{font-size:17px;font-weight:600;margin-left:10px;letter-spacing:0}
  .shi{display:flex;gap:20px}
  .pills{display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-bottom:12px}
  .bp{display:flex;align-items:center;gap:12px;background:${t.surface};border-radius:40px;padding:12px;${t.glass ? 'border:1px solid ' + t.cardLine + ';' : ''}}
  .bp b{display:block;font-size:16px}.bp span{display:block;font-size:12px;color:${t.sub};margin-top:2px}
  .bp.sm2 .qc{width:50px;height:50px}
  .panel{background:${t.surface};border-radius:32px;padding:18px 10px 10px;margin-bottom:12px;${t.glass ? 'border:1px solid ' + t.cardLine + ';' : ''}}
  .grid{display:grid;grid-template-columns:repeat(4,1fr);row-gap:14px}
  .handle{width:36px;height:4px;border-radius:2px;background:${t.sub};opacity:.4;margin:12px auto 0}
  .slider{display:flex;align-items:center;gap:12px;background:${t.surface};border-radius:36px;padding:12px 12px 12px 16px;margin-bottom:10px}
  .track{flex:1;height:46px;border-radius:23px;background:${t.surface2};position:relative;overflow:hidden}
  .fill{height:100%;background:${t.light ? '#c6d3f7' : t.surface2};display:flex;align-items:center;justify-content:flex-end;padding-right:12px;border-radius:23px;color:${t.text}}
  ${t.glass ? '.fill{background:rgba(79,224,189,.28)}' : ''}${t.id === 'amber' ? '.fill{background:#7a6f66}' : ''}
  .sl{position:absolute;right:16px;top:50%;transform:translateY(-50%);font-size:13px;color:${t.sub};font-weight:600}
  .qc.sm{width:52px;height:52px}
  .media{display:flex;align-items:center;gap:14px;background:${t.surface};border-radius:36px;padding:14px 14px 14px 22px;margin-bottom:12px}
  .media b{display:block;font-size:15px}.media span{display:block;font-size:12px;color:${t.sub}}
  .mp{color:${t.text}}.out{margin-left:auto;font-size:13px;font-weight:700;background:${t.surface2};padding:10px 14px;border-radius:20px}
  .notes{flex:1;font-size:15px;line-height:1.6}
  .notes h3{font-size:17px;margin:0 0 8px;color:${t.accent}}
  .notes section{border-top:1px solid ${t.light ? 'rgba(0,0,0,.1)' : 'rgba(255,255,255,.12)'};padding:14px 0}
  .notes ul{padding-left:18px}.notes li{margin:3px 0;opacity:.85}
  .sw{display:flex;gap:8px;margin-top:6px}.sw i{width:34px;height:34px;border-radius:10px;display:block;border:1px solid rgba(128,128,128,.3)}`;
}

for (const t of themes) {
  const sw = [t.accent, t.hot, t.danger, t.tileOff, t.text].map(c => `<i style="background:${c}"></i>`).join('');
  const html = `<!doctype html><html><head><meta charset="utf-8"><style>${css(t)}</style></head><body>
  <div class="bt"><div><div class="tag">알림 청소부 · UI 테마 시안 · ${t.tag}</div><h1>${t.name}</h1><p>${t.desc}</p></div></div>
  <div class="row">
    <div>${mainScreen(t)}<div class="cap">① 게임 화면 · 알림을 옆으로 밀어 정리</div></div>
    <div>${shadeScreen(t)}<div class="cap">② 위에서 끌어내린 패널 · 상점·강화·설정</div></div>
    <div class="notes">
      <section style="border-top:0;padding-top:0"><h3>상태표시줄 = 게임 HUD</h3><ul>
        <li>배터리 = 행동력 (9/12, 충전 중)</li><li>신호 막대 = 과부하 단계</li><li>반짝이 아이콘 = 진행 중 이벤트·부스트</li><li>시계는 실제 시각</li></ul></section>
      <section><h3>상단 원형 버튼 4개</h3><ul><li>자동 정리 · 과부하 게이지(링) · 2배 보상 · 알림훈련</li><li>켜짐 = 포인트 색으로 채움, 꺼짐 = 회색</li></ul></section>
      <section><h3>게임 영역</h3><ul><li>잠금화면 알림 카드. 숫자/기준과 진행 막대로 폭발까지 남은 양</li><li>미는 카드는 포인트 색 테두리와 잔상, 합쳐지는 카드는 발광</li><li>연쇄 문구는 이유 하나만: “메일소포 ×1.60”</li></ul></section>
      <section><h3>끌어내린 패널</h3><ul><li>큰 버튼: 앱 상점 · 앱 강화</li><li>밝기 슬라이더 자리 = 배경음, 볼륨 자리 = 효과음</li><li>미디어 카드 = 배경음 트랙 선택</li><li>아래 큰 버튼: 최고 기록 · 일일 도전</li></ul></section>
      <section><h3>색</h3><div class="sw">${sw}</div><div style="font-size:13px;opacity:.6;margin-top:6px">포인트 · 연쇄 · 돌발 · 꺼진 타일 · 본문</div></section>
    </div></div></body></html>`;
  fs.writeFileSync(`theme-${t.id}.html`, html);
}
