# FreshBrowser – test your app as many "different users"

## Build
1. Open this folder in Android Studio (File > Open).
2. Let Gradle sync, plug in your phone (USB debugging on), press Run.
   Or: Build > Build APK(s) and copy app-debug.apk to the phone.

## Use
- Type your app URL and tap **Go**.
- Tap **New user** to wipe cookies/storage/cache, randomize the
  User-Agent + language, and reload: your app sees a brand-new visitor.
- Each identity sends an `X-Test-User-Id` header, so you can match
  attempts in your server logs.

## What is NOT changed
- **MAC address**: browsers can't change it, and servers never see it.
- **Public IP**: if your app blocks by IP, toggle airplane mode on
  mobile data (often gives a new IP), or use a VPN.

## Download the APK (no Android Studio needed)
Every push to the `freshbrowser` branch builds the app automatically with GitHub Actions.
Get it from **Releases → FreshBrowser latest build → app-debug.apk**, open it on your
phone and allow "install unknown apps".

## iPhone version (`ios/` folder)
GitHub builds an **unsigned** `.ipa` on every change to `ios/`:
Releases → FreshBrowser iOS latest build → FreshBrowser-unsigned.ipa.

Apple only installs signed apps, so sign it on install with one of:
- **Sideloadly** or **AltStore** (Windows/Mac) + free Apple ID —
  works, but the app expires after 7 days (just re-install).
- A paid Apple Developer account ($99/yr) — lasts 1 year.

Each "New user" uses a fresh in-memory WebKit data store (cookies,
storage, cache) + random User-Agent/language. IP is not changed.
