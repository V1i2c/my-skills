# Focus Harbor (Android)

Focus Harbor is an offline-first focus/blocking app for Android 12 and newer (`minSdk 31`). Its project lives in this directory and targets Android API 36.

## What it does

- Local focus profiles with app and website blocklists or allowlists, weekly schedules, time/launch quotas, optional Wi-Fi and geofence triggers, and auto-blocking for newly installed apps.
- Quick Block sessions with saved targets, a Quick Settings tile, and a home-screen widget.
- Best-effort sub-app section checks for selected social/video surfaces and supported browser address bars.
- Optional notification capture for blocked apps, on-device usage summaries and screen-unlock counts.
- Optional Strict Mode release conditions: a timer, schedule end, charging state, locally salted/hashed PIN, cooldown, and a reflection-plus-cooldown emergency path.
- Local JSON backup/restore and CSV import/export for the adult-domain list.

## First-run setup

1. Create a profile or configure Quick Block targets.
2. In Android **Settings → Accessibility**, enable **Focus Harbor app blocker**. The service observes foreground package names and, for supported browsers, address-bar text. Matching is performed on the device.
3. Grant **Notification access** only if you want blocked notifications cancelled and retained locally.
4. Grant location/Wi-Fi permissions only if you configure those triggers. Android may hide Wi-Fi names or require the user to grant background location for some geofence behavior.
5. Add the Quick Block tile from the system Quick Settings editor or add the Focus Harbor widget from the launcher.

Disabling/revoking a permission disables the corresponding feature. OEM battery management may pause background work; review the Android app settings if scheduled checks stop.

## Offline and privacy design

The manifest intentionally does **not** request `android.permission.INTERNET`. There are no accounts, cloud sync, remote approvals, telemetry, ads, or billing. Profiles, statistics, notification text, and PIN verifiers are stored locally with Preferences DataStore. Backups are files you explicitly save or import; the PIN itself is not stored, only a salted PBKDF2 verifier.

## Important platform limits

This is a regular Android app, not a device-owner or system app. Strict Mode is a commitment aid, not an unbypassable lock. Android Settings, force-stop, uninstall, reboot, accessibility revocation, and some OEM/browser behaviors cannot be reliably blocked. Website checks depend on browser accessibility trees exposing usable address-bar text; private tabs, embedded webviews, browser updates, and unsupported browsers can reduce coverage. Sub-app resource-ID heuristics can change with app updates. Usage statistics are approximate and are collected only while the accessibility service is running.

The optional adult-domain feature uses only the local list that you supply or import; it does not download a category feed, install a VPN, or change DNS.

## Build and test

Requirements: JDK 17, Android SDK platform 36, and Android build tools 36.0.0.

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

GitHub Actions runs the unit tests, builds the debug APK, and publishes `focus-harbor-debug` as a short-lived workflow artifact.
