// Bundles pfx-entry.js → pfx.bundle.js (IIFE, window.PFX). Needs esbuild: npx esbuild …
import { execSync } from "child_process";
const here = new URL(".", import.meta.url).pathname;
execSync(`npx --yes esbuild@0.24.0 ${here}pfx-entry.js --bundle --format=iife --global-name=PFX --minify --outfile=${here}pfx.bundle.js`, { stdio: "inherit" });
