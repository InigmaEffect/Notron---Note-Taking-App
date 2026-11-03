# Syncing Notron between your phone and PC (Notron 4.4)

Notron keeps your notes as plain Markdown files in a folder. To move that folder between devices you use a
file-sync app; Notron then reacts instantly to whatever arrives (live updates, merging, older-copy safety net).
The recommended sync app is **Syncthing**: open source, widely audited, no account, no cloud. Devices talk directly
to each other over your Wi-Fi with TLS and each device is identified by a certificate, so only devices you approve can connect.

## One-time setup (about 10 minutes)

1. **PC:** install Syncthing (syncthing.net). Open its page (http://127.0.0.1:8384) and set a GUI password (Actions > Settings > GUI).
2. **Phone:** install **Syncthing-Fork** (F-Droid or GitHub; it is the maintained Android build - check that it is still current).
3. On the PC create/choose the vault folder, e.g. `Documents/Vault`. Open it in Notron on the PC (Chrome/Edge, "Open folder as vault").
4. In Syncthing on the PC: **Add Folder** > pick that folder > Folder type "Send & Receive".
5. Pair the devices: PC > Actions > Show ID (QR). Phone > Devices > + > scan. Accept the request on the other side. Share the folder with the phone.
6. On the phone, accept the folder and put it inside shared storage, e.g. `Documents/Vault` (Android 11+ does not let apps use Downloads or the storage root).
7. Open Notron on the phone > Open folder as vault > choose that same folder.

## Make it LAN-only and private
Syncthing > Settings > Connections (both devices):
- turn **off**: Global Discovery, Enable Relaying, NAT traversal
- keep **on**: Local Discovery
- Phone: run only on Wi-Fi (and optionally only on your home network), and exclude Syncthing-Fork from battery optimisation so it keeps running.

## Safety net
- Folder > Edit > File Versioning > **Staggered** (or Simple). Old versions land in the hidden `.stversions` folder.
- Notron never overwrites your work on a clash: when the same lines were changed on two devices the newer edit wins and the other is
  saved under Settings > Vault > Sync between devices > Older copies. Edits on different lines are merged automatically.

## What Notron does for you
- Watches the vault folder: a note changed on the other device appears within a few seconds (the open note updates in place, scroll position kept).
- Checks immediately when you open or return to the app.
- **Sync now** (Settings > Vault) forces an immediate re-check. **Rebuild from disk** re-reads every file.
- Created / last-opened dates are kept in `.notron/devices/<device>.json` (one file per device, so devices never clash).
  Last modified comes from the file's own modified time.

## Good to know
- Syncthing can only sync while both devices are online and Syncthing is running on both.
- If Syncthing itself sees a clash it creates `Name.sync-conflict-....md`; Notron shows those as normal notes so nothing is hidden.
- Renaming a note on one device looks like "delete + new note" to other devices; created-date history is not carried across a rename.
- This protects notes in transit and from a lost cloud account. It does not encrypt them on each device. For card numbers, passwords or
  anything truly critical use a password manager (KeePassXC / Bitwarden), not a notes app.
