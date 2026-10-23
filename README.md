# Notron – Stable 1.01

## Deploy on Vercel (2 minutes)
1. Put this folder in a GitHub repo (or use the Vercel CLI: `npx vercel --prod` inside this folder).
2. On vercel.com choose **Add New → Project → import the repo**.
3. Framework preset: **Other**. Build command: none. Output directory: leave empty (root).
4. Deploy. Open the URL on your phone in Chrome → menu → **Install app** (or Add to Home screen).

## Notes
- Notes are stored on the device (IndexedDB), work fully offline, and persist between launches.
- Settings → Backup downloads a zip of every note and image (same folders; opens in Obsidian). Import from zip restores it.
- Use the same URL on PC and phone; each device keeps its own notes until sync is added. Move notes between devices with Backup / Import.
- After an update, close and reopen the app once or twice (offline cache refreshes in the background).
