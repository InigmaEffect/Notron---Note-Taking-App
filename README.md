# Notron – Android app (v4.1)

This folder wraps the Notron web app in a real Android app (fullscreen, no browser, no pull-to-refresh, back button handled).
The web app itself is the single file `www/index.html`.

## Build the APK with no software installed (GitHub)
1. Make a free account at github.com and create a **private** repository (private matters: the signing key is inside).
2. Upload everything in this folder to the repo (drag the unzipped files in; make sure the `.github` folder goes too).
   If `.github` is missing: in the repo open **Actions → set up a workflow yourself**, paste the contents of `BUILD-APK-WORKFLOW.yml`, commit.
3. Open the **Actions** tab → **Build Android APK** → **Run workflow**. Wait about 5–8 minutes.
4. Open the finished run → download the **Notron-apk** artifact (a zip) → unzip → `app-release.apk`.
5. Send the APK to your phone, open it, allow “install unknown apps” for your file manager/browser when asked, install.

## Build on a PC instead (Android Studio)
1. Install Node 22, JDK 21 and Android Studio.
2. In this folder run `npm install` then `npx cap sync android`.
3. Open the `android` folder in Android Studio → Build → Build APK(s). Or run `cd android && ./gradlew assembleRelease`.

## Updating the app later
Replace `www/index.html` with the newer Notron file, commit, run the workflow again, install the new APK over the old one.
Updates keep your notes because every build is signed with the same key (`keystore/notron.keystore`). Never delete or change that key.

## Important
- Notes live inside the app on the phone. Uninstalling the app deletes them. Use Settings → Backup regularly.
- **Vaults are real phone folders.** v4.1 adds a native folder picker (`NotronFSPlugin.java`, Android's Storage Access Framework). Tap *Open folder as vault* to use an existing folder (e.g. one Obsidian uses), or *Create new vault* to make a fresh one inside a folder you choose. Android does not allow Downloads or the top of storage; Documents or a folder you create works. The permission is remembered, so the app reopens straight into your vault.
- *Use in-app storage* on the first screen still works as a fallback.
- The v4.1 folder plugin was written without being compiled by the author. If the GitHub build fails, open the failed step and send the red error text.
