// pixelfx (MIT, patch-gg/pixelfx) bundled for the game page: window.PFX. Rebuild: node tools/game-page/build-pfx.mjs
export { createFrame, clearFrame, createFx, composite } from "./vendor/pixelfx/src/core/index.js";
import * as hitSpark from "./vendor/pixelfx/src/fx/impacts/hit-spark.js";
import * as critHit from "./vendor/pixelfx/src/fx/impacts/crit-hit.js";
import * as bluntHit from "./vendor/pixelfx/src/fx/impacts/blunt-hit.js";
import * as heavyLanding from "./vendor/pixelfx/src/fx/impacts/heavy-landing.js";
import * as impactFrame from "./vendor/pixelfx/src/fx/impacts/impact-frame.js";
import * as smallExplosion from "./vendor/pixelfx/src/fx/explosions/small-explosion.js";
import * as fireworkBurst from "./vendor/pixelfx/src/fx/explosions/firework-burst.js";
import * as collectBurst from "./vendor/pixelfx/src/fx/pickups/collect-burst.js";
import * as coinCollect from "./vendor/pixelfx/src/fx/ui/coin-collect.js";
import * as confetti from "./vendor/pixelfx/src/fx/ui/confetti.js";
import * as crtOff from "./vendor/pixelfx/src/fx/transitions/crt-off.js";
import * as zapHit from "./vendor/pixelfx/src/fx/lightning/zap-hit.js";
import * as chargeUp from "./vendor/pixelfx/src/fx/lightning/charge-up.js";
export const FX = { hitSpark, critHit, bluntHit, heavyLanding, impactFrame, smallExplosion, fireworkBurst, collectBurst, coinCollect, confetti, crtOff, zapHit, chargeUp };
