# Notron 4.5.1.1 – desktop (PC) fix only

The Android app (`www/`, `android/`, workflow, keystore) is untouched. No new APK or reinstall is needed.

- `desktop/` is the PC app: `desktop/index.html` + manifest + icons.
- `vercel.json` tells Vercel to publish ONLY `desktop/` (fixes the "page does not exist" error and keeps
  `keystore/` and `android/` off the public internet).
- Opening `desktop/index.html` straight from disk still works for a quick test, but browsers cannot remember
  the vault folder between visits when a page is opened as a file. Use the Vercel address for daily use.

Deploying: push these files to the repo Vercel is connected to. In Vercel > Project > Settings > Build & Development,
leave Framework = Other and clear any Output Directory / Build Command overrides (vercel.json supplies them), then redeploy.
Use Chrome, Edge or another Chromium browser (folder access is not available in Firefox/Safari).
