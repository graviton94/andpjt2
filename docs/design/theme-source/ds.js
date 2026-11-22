// Renders the design-system sheets (light + dark) from ../tokens.json.
//   npm install && node ds.js && node shot.js 2 ds-light ds-dark
const fs = require('fs');
const path = require('path');
const { ic } = require('./icons.js');
const T = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'tokens.json'), 'utf8'));
const font = path.resolve(__dirname, 'node_modules/pretendard/dist/public/variable/PretendardVariable.ttf');
const ty = (k) => { const t = T.type[k]; return `font-size:${t.size}px;line-height:${t.line}px;font-weight:${t.weight};letter-spacing:${t.tracking}em`; };

const apps = {
  chat: ['톡탁톡', '#7c5cff', '톡'], mail: ['메일소포', '#3b9bff', '@'], delivery: ['문앞왔', '#2fbf71', '◆'],
  feed: ['피드또봐', '#ff5fa2', '✿'], calendar: ['일정찍', '#f2a71b', '22'], music: ['비트서랍', '#14b8c4', '♪'],
};

function page(modeKey) {
  const M = T.modes[modeKey];
  const c = M.color;
  const dark = modeKey === 'dark';
  const g = (lvl) => { const x = M.glass[lvl]; return `background:${x.fill};backdrop-filter:blur(${x.blur}px) saturate(1.4);-webkit-backdrop-filter:blur(${x.blur}px);border:1px solid ${x.border};box-shadow:0 8px 24px ${x.shadow}`; };
  const bg = `radial-gradient(60% 34% at 92% 8%, ${M.background.blobA}, transparent 70%), radial-gradient(55% 30% at 0% 62%, ${M.background.blobB}, transparent 70%), linear-gradient(180deg, ${M.background.top}, ${M.background.base} 45%)`;

  const status = `<div class="sb"><span>10:24</span><span class="sbr"><span style="color:${c.chain}">${ic('sparkle', 15)}</span>${ic('bt', 15)}${ic('wifi', 16)}
    <span class="sig"><i></i><i></i><i></i><i class="off"></i></span><span class="bat"><b style="width:75%"></b><em>9/12</em></span></span></div>`;
  const tile = (icon, label, sub, state = '') => `<div class="q"><div class="qc ${state}">${ic(icon, 24)}</div><div class="ql">${label}</div>${sub ? `<div class="qs">${sub}</div>` : ''}</div>`;
  const card = (app, msg, t, n, th, cls = '') => {
    const [name, col, glyph] = apps[app] || ['스팸', c.interrupt, '!'];
    const interrupt = cls.includes('interrupt');
    return `<div class="card ${cls}">${cls.includes('swiping') ? '<div class="trail"></div>' : ''}<div class="ci" style="background:${col}">${glyph}</div>
      <div class="cb"><div class="ch"><b>${name}</b><span>${t}</span></div><div class="cm">${msg}</div>${interrupt ? '' : `<div class="bar"><i style="width:${n / th * 100}%"></i></div>`}</div>
      <div class="cn">${interrupt ? '<span class="chip">돌발</span>' : `<b>${n}</b><span>/${th}</span>`}</div></div>`;
  };

  const game = `<div class="phone">${status}
    <div class="head"><div><div class="big">128.4K<small> P</small></div><div class="sub">Lv 7 · 다음 레벨까지 32%</div></div><div class="mult"><span>총 배율</span><b>×24.6</b></div></div>
    <div class="xp"><i style="width:68%"></i></div>
    <div class="quick glass-panel">${tile('auto', '자동 정리', '4.5초', 'on')}${tile('bolt', '과부하', '68%', 'ring')}${tile('ad', '2배 보상', '대기')}${tile('siren', '알림훈련', '잠김', 'disabled')}</div>
    <div class="secth"><b>알림 7개</b><span>폭발 기준 4</span></div>
    <div class="cards">
      ${card('chat', '조용한 단톡방에 메시지가 쌓였어요', '지금', 1, 4)}
      ${card('mail', '중요 표시한 메일이 더 왔어요', '1분 전', 2, 4)}
      ${card('delivery', '도착 2분 전이라고 한 지 8분 됐어요', '2분 전', 1, 4, 'swiping')}
      ${card('mail', '받은편지함에 새 메일', '3분 전', 2, 4, 'merge')}
      ${card(null, '광고 알림 · 3번 안에 지우지 않으면 복제', '4분 전', 0, 0, 'interrupt')}
      ${card('feed', '이미 본 피드를 다시 추천합니다', '6분 전', 3, 4)}
      ${card('chat', '단톡방 공지가 바뀌었어요', '9분 전', 2, 4)}
      <div class="combo">1연쇄!<small>메일소포 4/4 · ×2 · +80 P</small></div>
    </div>
    <div class="hint">${ic('chev', 18)} 위에서 끌어내리면 상점·강화·설정</div><div class="nav"><i></i></div></div>`;

  const grid = [['vib', '진동', 'on'], ['motion', '움직임 줄이기', ''], ['eye', '미리보기', 'on'], ['moon', '다크 모드', dark ? 'on' : ''],
    ['trophy', '기록', ''], ['cal', '일일 도전', ''], ['ad', '광고 보상', ''], ['help', '도움말', '']].map(([i, l, s]) => tile(i, l, '', s)).join('');
  const shade = `<div class="phone">${status}
    <div class="sh"><div class="shc">10:24<span>11월 23일 월요일</span></div><div class="shi">${ic('pencil')}${ic('power')}${ic('gear')}</div></div>
    <div class="pills"><div class="bp glass-panel"><div class="qc on">${ic('bag', 24)}</div><div><b>앱 상점</b><span>다음 · 1,250 P</span></div></div>
      <div class="bp glass-panel"><div class="qc on">${ic('up', 24)}</div><div><b>앱 강화</b><span>3개 강화 가능</span></div></div></div>
    <div class="panel glass-panel"><div class="grid">${grid}</div><div class="handle"></div></div>
    <div class="slider glass-panel"><div class="track"><div class="fill" style="width:62%">${ic('note', 20)}</div><span class="sl">배경음</span></div><div class="qc on sm">${ic('note', 22)}</div></div>
    <div class="slider glass-panel"><div class="track"><div class="fill" style="width:38%">${ic('sfx', 20)}</div><span class="sl">효과음</span></div><div class="qc on sm">${ic('vib', 22)}</div></div>
    <div class="media glass-panel"><div style="color:${c.text}">${ic('play', 20)}</div><div><b>Overload Shuffle</b><span>알림 청소부 OST · 2/6</span></div><div class="out">트랙 목록</div></div>
    <div class="pills"><div class="bp glass-panel"><div class="qc sm2">${ic('trophy', 22)}</div><div><b>최고 기록</b><span>4연쇄 · 21,233 P</span></div></div>
      <div class="bp glass-panel"><div class="qc sm2">${ic('cal', 22)}</div><div><b>일일 도전</b><span>오늘 남은 1회</span></div></div></div>
    <div class="nav"><i></i></div></div>`;

  const sw = (name, hex) => `<div class="sw"><i style="background:${hex}"></i><b>${name}</b><span>${hex}</span></div>`;
  const colors = Object.entries(c).filter(([k]) => !['scrim', 'flash', 'accentSoft'].includes(k)).map(([k, v]) => sw(k, v)).join('');
  const glassDemo = ['panel', 'card', 'raised'].map(l => `<div class="gl" style="${g(l)}"><b>${l}</b><span>fill ${M.glass[l].fill}<br>blur ${M.glass[l].blur} · 12 미만 ${M.glass[l].fallbackFill}</span></div>`).join('');
  const typeDemo = ['display', 'headline', 'title', 'body', 'bodySub', 'label', 'caption'].map(k => `<div class="tyrow"><span class="tyk">${k} ${T.type[k].size}/${T.type[k].weight}</span><span style="${ty(k)}">${k === 'display' ? '128.4K P' : k === 'headline' ? '×24.6' : '알림 청소부 1,773 P'}</span></div>`).join('');
  const shopRow = (app, tierLabel, line, price, state) => { const [n, col, gl] = apps[app]; return `<div class="row glass-card"><div class="ci" style="background:${col}">${gl}</div><div class="cb"><div class="ch"><b>${n}</b><span class="chip soft">${tierLabel}</span></div><div class="cm">${line}</div></div><div class="btn ${state}">${price}</div></div>`; };

  const comps = `<div class="comps">
    <h3>색 역할</h3><div class="sws">${colors}</div>
    <h3>유리 표면 3단계</h3><div class="gls">${glassDemo}</div>
    <h3>글자</h3>${typeDemo}
    <h3>원형 타일 상태</h3><div class="tiles glass-panel">${tile('auto', '켜짐', '', 'on')}${tile('ad', '꺼짐', '')}${tile('bolt', '게이지', '68%', 'ring')}${tile('siren', '잠김', 'Lv 9', 'disabled')}${tile('bag', '눌림', '', 'on pressed')}</div>
    <h3>알림 카드 상태</h3><div class="cards demo">
      ${card('calendar', '기본 · 수량/기준과 진행 막대', '1분 전', 2, 4)}
      ${card('calendar', '폭발 직전 · 3/4는 막대가 연쇄 색', '1분 전', 3, 4, 'ready')}
      ${card('mail', '합치기 · 연쇄 색 발광', '지금', 4, 4, 'merge')}
      ${card(null, '돌발 · 스팸·전화', '지금', 0, 0, 'interrupt')}</div>
    <h3>상점·강화 줄</h3>
      ${shopRow('feed', '2티어 · 알림 가치 ×10', '레벨 3에서 열림 · 생활 · 글로벌', '1,250 P', '')}
      ${shopRow('chat', 'Lv 9 → 10', '알림 가치 9 → 20 · 마일스톤 ×2', '18 P', 'hot')}
      ${shopRow('music', '3티어', '레벨 8에서 열림', 'Lv 8', 'locked')}
  </div>`;

  const css = `@font-face{font-family:P;src:url('file://${font}')}
  *{box-sizing:border-box;margin:0;padding:0}
  body{font-family:P,sans-serif;font-feature-settings:'tnum';background:${dark ? '#0d0b09' : '#e4e8f0'};color:${c.text};width:1840px;padding:44px 52px}
  h1{${ty('display')}}.lead{font-size:16px;color:${c.textSub};margin:8px 0 26px;max-width:1100px;line-height:1.55}.tag{font-size:15px;font-weight:700;color:${c.accent};margin-bottom:6px}
  .row0{display:flex;gap:34px;align-items:flex-start}
  .phone{width:412px;height:900px;border-radius:40px;overflow:hidden;position:relative;background:${bg};padding:0 ${T.space.screen}px;flex:none;
    box-shadow:0 30px 80px ${dark ? '#000a' : '#1e2a5030'},0 0 0 8px ${dark ? '#2b2622' : '#cfd4de'}}
  .glass-panel{${g('panel')}}.glass-card{${g('card')}}
  .sb{display:flex;justify-content:space-between;align-items:center;height:${T.size.statusBar}px;padding:6px 8px 0;font-size:15px;font-weight:600}
  .sbr{display:flex;gap:7px;align-items:center}
  .sig{display:flex;gap:2px;align-items:flex-end;height:13px}.sig i{width:3px;background:currentColor;border-radius:1px}.sig i:nth-child(1){height:5px}.sig i:nth-child(2){height:8px}.sig i:nth-child(3){height:11px}.sig i:nth-child(4){height:13px}.sig i.off{opacity:.3}
  .bat{position:relative;width:42px;height:19px;border-radius:10px;background:${c.track};overflow:hidden;display:flex;align-items:center;justify-content:center}
  .bat b{position:absolute;left:0;top:0;bottom:0;background:${c.accent}}.bat em{position:relative;font-style:normal;font-size:11px;font-weight:800;color:${c.onAccent}}
  .head{display:flex;justify-content:space-between;align-items:flex-end;padding:14px 6px 8px}
  .big{${ty('display')}}.big small{font-size:20px;color:${c.textSub};font-weight:600}.sub{font-size:14px;color:${c.textSub};margin-top:6px}
  .mult{text-align:right}.mult span{display:block;${ty('caption')};color:${c.textSub}}.mult b{${ty('headline')};font-size:24px;color:${c.accent}}
  .xp{height:4px;border-radius:2px;background:${c.track};margin:4px 6px 14px}.xp i{display:block;height:100%;border-radius:2px;background:${c.accent}}
  .quick{display:grid;grid-template-columns:repeat(4,1fr);border-radius:${T.radius.panel}px;padding:14px 6px 12px}
  .q{display:flex;flex-direction:column;align-items:center}
  .qc{width:${T.size.tile}px;height:${T.size.tile}px;border-radius:50%;display:flex;align-items:center;justify-content:center;background:${c.tileOff};color:${c.onTileOff};position:relative}
  .qc.on{background:${dark ? `linear-gradient(145deg, ${c.chainGlow}, ${c.accent})` : c.accent};color:${c.onAccent};box-shadow:0 6px 18px ${c.accent}55}
  .qc.pressed{transform:scale(.92);filter:brightness(.92)}
  .qc.disabled{opacity:.38}
  .qc.ring{background:conic-gradient(${c.chain} 0 68%, ${c.tileOff} 68% 100%);color:${c.text}}
  .qc.ring svg{position:relative;z-index:1}.qc.ring::after{content:'';position:absolute;inset:5px;border-radius:50%;background:${dark ? '#2a241e' : '#f4f6fb'}}
  .qc.sm{width:52px;height:52px}.qc.sm2{width:${T.size.tileSmall}px;height:${T.size.tileSmall}px}
  .ql{${ty('label')};margin-top:7px}.qs{${ty('caption')};color:${c.textSub};margin-top:1px}
  .secth{display:flex;justify-content:space-between;align-items:baseline;padding:18px 8px 8px}.secth b{font-size:15px}.secth span{${ty('caption')};color:${c.textSub}}
  .cards{display:flex;flex-direction:column;gap:6px;position:relative}
  .card,.row{position:relative;display:flex;align-items:center;gap:12px;border-radius:${T.radius.card}px;padding:10px 14px 10px 12px;height:${T.size.card}px;${g('card')}}
  .ci{width:${T.size.appIcon}px;height:${T.size.appIcon}px;border-radius:${T.radius.icon}px;display:flex;align-items:center;justify-content:center;color:#fff;font-weight:800;font-size:15px;flex:none}
  .cb{flex:1;min-width:0}.ch{display:flex;gap:6px;align-items:center}.ch b{${ty('body')}}.ch span{${ty('caption')};color:${c.textSub}}
  .cm{${ty('bodySub')};color:${c.textSub};white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
  .bar{height:3px;border-radius:2px;background:${c.track};margin-top:5px;width:86%}.bar i{display:block;height:100%;border-radius:2px;background:${c.accent}}
  .cn{text-align:right;min-width:36px}.cn b{font-size:16px}.cn span{${ty('caption')};color:${c.textSub}}
  .chip{${ty('caption')};font-weight:700;padding:3px 8px;border-radius:${T.radius.chip}px;background:${c.interrupt};color:#fff}.chip.soft{background:${c.accentSoft};color:${c.accent}}
  .card.interrupt{border-color:${c.interrupt}88;background:linear-gradient(90deg,${c.interrupt}26,${M.glass.card.fill})}
  .card.ready .bar i{background:${c.chain}}.card.ready .cn b{color:${c.chain}}
  .card.merge{box-shadow:0 0 0 2px ${c.chain},0 0 26px ${c.chainGlow}aa}
  .card.swiping{transform:translateX(78px) rotate(1.2deg);z-index:2;border-color:${c.accent};box-shadow:0 14px 30px ${M.glass.raised.shadow}}
  .trail{position:absolute;right:100%;top:0;bottom:0;width:90px;margin-right:-6px;border-radius:20px 0 0 20px;background:linear-gradient(90deg,transparent,${c.accent}99)}
  .combo{position:absolute;right:4px;top:112px;transform:rotate(-6deg);${ty('combo')};color:${c.chain};text-align:right;z-index:3;
    text-shadow:0 3px 0 ${dark ? '#000' : '#b35a00'},0 0 22px ${c.chainGlow}}
  .combo small{display:block;font-size:13px;font-weight:700;color:${c.text};text-shadow:none;margin-top:6px;padding:4px 8px;border-radius:8px;${g('raised')}}
  .hint{display:flex;gap:6px;align-items:center;justify-content:center;${ty('caption')};color:${c.textSub};margin-top:16px}
  .nav{position:absolute;bottom:10px;left:0;right:0;display:flex;justify-content:center}.nav i{width:120px;height:${T.size.navHandle}px;border-radius:3px;background:${c.text};opacity:.45}
  .sh{display:flex;justify-content:space-between;align-items:center;padding:16px 8px 18px}.shc{${ty('display')};font-size:40px}.shc span{font-size:17px;font-weight:600;margin-left:10px;letter-spacing:0}.shi{display:flex;gap:20px}
  .pills{display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-bottom:12px}
  .bp{display:flex;align-items:center;gap:12px;border-radius:40px;padding:12px}.bp b{display:block;${ty('title')}}.bp span{display:block;${ty('caption')};color:${c.textSub};margin-top:2px}
  .panel{border-radius:${T.radius.sheet}px;padding:18px 10px 10px;margin-bottom:12px}.grid{display:grid;grid-template-columns:repeat(4,1fr);row-gap:14px}
  .handle{width:36px;height:4px;border-radius:2px;background:${c.textFaint};margin:12px auto 0}
  .slider{display:flex;align-items:center;gap:12px;border-radius:36px;padding:12px 12px 12px 16px;margin-bottom:10px}
  .track{flex:1;height:${T.size.slider}px;border-radius:23px;background:${c.track};position:relative;overflow:hidden}
  .fill{height:100%;background:${c.trackFill};display:flex;align-items:center;justify-content:flex-end;padding-right:12px;border-radius:23px;color:${c.text}}
  .sl{position:absolute;right:16px;top:50%;transform:translateY(-50%);${ty('label')};color:${c.textSub}}
  .media{display:flex;align-items:center;gap:14px;border-radius:36px;padding:14px 14px 14px 22px;margin-bottom:12px}.media b{display:block;font-size:15px}.media span{display:block;${ty('caption')};color:${c.textSub}}
  .out{margin-left:auto;${ty('label')};font-weight:700;background:${c.track};padding:10px 14px;border-radius:20px}
  .comps{flex:1;min-width:0;padding:22px 26px;border-radius:${T.radius.sheet}px;background:${bg};${dark ? '' : ''}box-shadow:0 0 0 1px ${M.glass.panel.border}}
  .comps h3{${ty('title')};color:${c.accent};margin:18px 0 10px}.comps h3:first-child{margin-top:0}
  .sws{display:grid;grid-template-columns:repeat(3,1fr);gap:8px}.sw{display:flex;align-items:center;gap:8px}.sw i{width:28px;height:28px;border-radius:8px;border:1px solid ${c.track};flex:none}.sw b{font-size:12.5px}.sw span{font-size:11px;color:${c.textSub}}
  .gls{display:grid;grid-template-columns:repeat(3,1fr);gap:10px}.gl{border-radius:${T.radius.card}px;padding:12px}.gl b{display:block;${ty('body')}}.gl span{${ty('caption')};color:${c.textSub}}
  .tyrow{display:flex;align-items:baseline;gap:14px;margin:2px 0}.tyk{width:130px;${ty('caption')};color:${c.textFaint};flex:none}
  .tiles{display:grid;grid-template-columns:repeat(5,1fr);border-radius:${T.radius.panel}px;padding:14px 6px 12px}
  .cards.demo{gap:6px}.row{margin-bottom:6px}
  .btn{${ty('label')};font-weight:700;padding:9px 14px;border-radius:20px;background:${c.accent};color:${c.onAccent};white-space:nowrap}
  .btn.hot{background:${c.chain};color:${dark ? '#1e1408' : '#fff'}}.btn.locked{background:${c.tileOff};color:${c.textFaint}}
  .cap{${ty('label')};color:${c.textSub};margin:14px 0 0 6px}`;

  return `<!doctype html><html><head><meta charset="utf-8"><style>${css}</style></head><body>
    <div class="tag">알림 청소부 · 디자인 시스템 정본 v1 · tokens.json</div><h1>${M.name}</h1>
    <div class="lead">${dark ? '다크 모드. 숯빛 배경 위 앰버→골드. 켜진 타일은 골드에서 앰버로 내려가는 그라데이션, 연쇄는 골드 발광. 유리는 흰색 7~14% 위에 배경의 따뜻한 번짐이 비친다.' : '기본 테마. 옅은 블루·피치 번짐 위에 흰 유리. 포인트는 블루 한 색이고, 연쇄·폭발은 오렌지가 맡는다. 폭발 섬광은 밝은 바탕에서 보이도록 어두운 플래시로 반전한다.'}</div>
    <div class="row0"><div>${game}<div class="cap">게임 화면</div></div><div>${shade}<div class="cap">끌어내린 패널</div></div>${comps}</div></body></html>`;
}

for (const m of ['light', 'dark']) fs.writeFileSync(path.join(__dirname, `ds-${m}.html`), page(m));
