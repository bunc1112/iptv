# Bunc — IPTV Player (MVP)

Your own Android IPTV player for **phones, tablets, and Android TV / fire-stick boxes**.
Written from scratch in Kotlin + Jetpack Compose + Media3 (ExoPlayer). No other
vendor's app or code is involved, so there is no "not original" warning — this app
*is* the original.

## What it does (MVP)
- **Login two ways:** Xtream Codes (server URL + username + password) **or** an M3U playlist URL.
- **Live TV, Movies, Series** sections (Xtream). M3U shows Live channels grouped by playlist group.
- **Category rail + 4-column grid** — bigger posters, easy D-pad aiming on a remote.
- **Series → episodes** drill-down.
- **Full-screen player** (Media3/ExoPlayer) with on-screen controls; HLS + MPEG-TS.
- **Remembers your login** and lands straight on Home next launch.
- **Bunc branding** throughout: app name, launcher icon, Android-TV banner, splash wordmark, night-city palette.

## Requirements
- **Android Studio** (Rabbit / 2026.2 or newer). It installs the JDK + Android SDK for you.
- First open needs internet so Gradle can download the SDK + libraries (one time).

## Build & run
1. **Open** this folder in Android Studio (Welcome screen → *Open*, not *New Project*).
2. Wait for **Gradle sync** to finish (downloads dependencies — a few minutes first time).
3. Get an APK one of two ways:
   - **Quick test build:** menu **Build → Build Bundle(s)/APK(s) → Build APK(s)**. This auto-signs with a debug key — fine for sideloading onto your own box.
   - **Release build:** **Build → Generate Signed App Bundle / APK → APK**. Use your own key, or the throwaway one included at `keystore/bunc.keystore` (alias `bunc`, store/key password `buncpass`).
4. Copy the resulting `app-*.apk` to your TV box / phone and install it (enable "unknown sources").

To run on the computer instead, use Android Studio's built-in emulator — pick an **Android TV** device image to test the 10-foot UI.

## How to log in
- **Xtream:** Server URL as `http://yourhost:port` (scheme optional, port usually included), then your username and password.
- **M3U:** paste the full playlist URL.

## Configuration knobs
- Grid columns: `ui/screen/HomeScreen.kt` → `GridCells.Fixed(4)`.
- Live stream container: `data/Repository.kt` → `liveUrl()` uses `.ts`; change to `.m3u8` if your panel prefers HLS.
- Colors / branding: `ui/theme/Theme.kt` and `res/` (icons in `res/mipmap-*`, wordmark in `res/drawable`).

## MVP limitations (next-round candidates)
- No EPG / guide, favorites, resume/continue-watching, or search yet.
- M3U path focuses on Live channels (no VOD/Series split).
- Series uses Xtream `get_series_info`; very large catalogs load per-category.
- Player is standard ExoPlayer controls; no catch-up / recording.

Package id: `com.bunc.tv` · versionName `1.0`.
