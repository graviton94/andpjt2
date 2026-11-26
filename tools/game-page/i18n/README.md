# Language files (game screen)

Every word the player sees comes from these files. The code never contains display text.

| File | What |
|---|---|
| `ko.json` | Korean, the **source** language. Every key starts here. |
| `en.json` | English (default for non-Korean devices). |
| `<lang>.json` | Any new language: same keys as `ko.json`, plus `lang_name` (the language's own name, shown in Settings). |
| `core-<lang>.json` | Labels the core engine sends in Korean, swapped on screen: `apps` (app id → name), `byId` (`backup.<id>`, `cloud.<id>`, `ad.<id>`, `item.<id>`, `ach.<id>`), `text` (core vocabulary that has no id yet: regions, categories, missions, tickets; numbers are `#`). Korean needs no core file. |

App names, notification lines and unique-effect lines live with the content: `docs/content/l10n/<lang>.json`, `docs/content/app-effects.<lang>.json`.

## In code
- `$t('key')`, `$t('key', a, b)` with placeholders `{0}`, `{1}`. Static HTML: `data-i18n="key"`, `data-i18n-aria="key"`.
- Never compare against display text; compare ids and kinds.

## The build refuses to ship a hole (`python3 tools/game-page/build.py`)
- Korean text in `game.tpl.html` code (comments are fine): fail.
- A key missing from any language, an extra key, or different `{n}` placeholders: fail.
- `$t('…')` or `data-i18n` with a key that does not exist: fail.
- In the running page, `I18N.missing` lists any key or core label that fell back; the language crawl checks it is empty.

## Language choice
Settings → Language (phone setting / each language). Order: Settings choice → `?lang=` → device language → English.

## Adding a language (e.g. `ja`)
1. Copy `en.json` to `ja.json`, translate the values, set `lang_name` to `日本語`.
2. Copy `core-en.json` to `core-ja.json`, translate.
3. Add `docs/content/l10n/ja.json` (apps, lines) and `docs/content/app-effects.ja.json`, and let `build.py` read them for `TEXTS` (today it reads ko and en).
4. Build (it lists every missing key), then run the language crawl in both themes.
