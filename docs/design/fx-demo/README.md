# FX 시연 페이지

바이블 19장 타격감 사다리를 브라우저에서 바로 눌러 보는 시연판. 발행본: https://claude.ai/artifact/5zuf2fwBU2HxaW84wsY2WS

- `fx-demo.tpl.html`: 페이지 (캔버스, 120Hz 고정 스텝, 히트스톱, 두 겹 합성, 앱 브랜드색 팔레트 램프)
- `fxapps.json`: 앱 10종 이름·브랜드색·아이콘(data URI)
- `handset.b64`: 발주 수화기 부품(`../app-icons/handset-part.png`) 128px data URI
- `phone-sfx.json`: 폰 소리 층(CC0 uisfx 알림음·잠금·깨우기 + 직접 합성 진동) data URI
- 빌드: `python3 -c "t=open('fx-demo.tpl.html').read(); open('fx-demo.html','w').write(t.replace('__APPS__', open('fxapps.json').read()).replace('__HANDSET__', repr(open('handset.b64').read())).replace('__PHONE__', open('phone-sfx.json').read()))"`
