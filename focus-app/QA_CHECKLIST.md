# Release and device QA checklist

Focus Harbor targets Android 12+ (`minSdk 31`) and is intended to work entirely offline. This file is a **test run sheet**, not evidence that device tests have passed. Record a result only after running the scenario on a real device or emulator; mark unavailable scenarios as blocked rather than passed.

## Automated release gates

The `Focus Harbor Android CI` workflow should pass on the exact release candidate commit:

- `:app:testDebugUnitTest`
- `:app:lintDebug`
- `:app:assembleDebug`
- An APK permission check against the **merged debug APK** to ensure no dependency introduced `android.permission.INTERNET`

The debug APK is a QA artifact, not a signed production release.

## Record for each device run

- Commit SHA and APK SHA-256
- Device model / manufacturer and Android version / API level
- Locale, time zone, and whether battery optimization is enabled
- Result: **Pass**, **Fail**, or **Blocked**; include steps, expected vs. actual behavior, and a redacted screenshot or log when useful

## Device scenarios

### Install, first run, and offline behavior

- [ ] Fresh-install on Android 12 (API 31) and a current supported Android release.
- [ ] Enable airplane mode before first launch; create and edit a profile, save local website rules, run Quick Block, and open Insights. Core setup and local behavior should remain usable without a network connection.
- [ ] Confirm the app explains the Accessibility and optional Notification permissions before sending the user to Android Settings.
- [ ] Revoke Accessibility access in Settings and return to the app. The UI should reflect revocation and blocking should stop; re-enable it and verify blocking resumes.

### Profiles and blocking

- [ ] Create a blocklist profile and an allowlist profile. Verify selected and unselected apps/sites follow the selected mode.
- [ ] Enable an app rule and open the target app. The pause overlay should identify the reason, offer Home and Focus Harbor actions, and remain usable with large text and TalkBack.
- [ ] Verify protected system surfaces (Settings, System UI, launcher, phone/emergency UI, and Focus Harbor) remain reachable even under an allowlist.
- [ ] Pause and resume a profile; confirm the pause expires at the displayed time. Toggle a profile off and back on.
- [ ] Enable auto-block-new-apps, install a test app, and verify the resulting rule follows blocklist/allowlist behavior.

### Schedule, trigger, and quota boundaries

- [ ] Test a weekday schedule immediately before its start, at its start, just before its end, and at its exclusive end.
- [ ] Test an overnight schedule on its start day and after midnight on the following day; also test a day not selected.
- [ ] Test saved Wi-Fi and geofence conditions with permissions granted, denied, revoked, and with Android returning an unknown SSID or no last-known location. Missing data should fail safely without a crash and should not be mistaken for a confirmed location match.
- [ ] Test hourly, daily, and weekly time and launch quotas just below and at the limit. Usage is sampled periodically, so verify the documented best-effort behavior rather than expecting millisecond precision.
- [ ] Verify Insights updates only while the Accessibility service is active and respects ignored-statistics selections.

### Website and sub-app checks

- [ ] In each supported browser available on the device, navigate directly to a blocked hostname and a subdomain; verify the host rule applies.
- [ ] Verify a lookalike hostname (for example, `notexample.com` for a rule on `example.com`) is not treated as a subdomain match.
- [ ] Test search text, private tabs, embedded web views, browser updates, and an unsupported browser. Confirm limitations are not presented as reliable blocking.
- [ ] Test each configured sub-app section on the corresponding app version. Treat resource-ID-based section detection as best effort and record app versions where it fails.

### Quick Block surfaces

- [ ] Save app and website targets, then start and stop Quick Block from the Home screen, Quick Settings tile, and home-screen widget.
- [ ] Keep the Quick Settings shade open, then start/stop a session from the Home screen; verify the tile changes without closing and reopening the shade.
- [ ] Start/stop from the Quick Settings tile and widget; verify the other surfaces update to the same active/inactive state.
- [ ] Import a backup that changes Quick Block state while the widget and tile are available; verify both surfaces refresh to the restored state.
- [ ] Confirm all three surfaces agree on active/inactive state after process restart and that an empty target set shows setup guidance instead of creating an empty session.
- [ ] Verify automatic expiry and behavior after changing the device clock or rebooting; a reboot may use wall-clock fallback because Android's monotonic clock resets.

### Strict Mode and emergency release

- [ ] Start Strict Mode with no release conditions, then with timer, schedule-end, charging, PIN, and cooldown requirements individually and in combination.
- [ ] Verify wrong PIN, unmet timer/schedule/charging conditions, and active cooldown are refused with a useful explanation; verify release succeeds only after all selected conditions pass.
- [ ] Test profile edit, duplicate, delete, pause, toggle, backup import, app restart, and device reboot while a locked Strict Mode session is active.
- [ ] Test emergency release reflection, exact commitment text, and cooldown; verify incomplete answers or early release are refused.
- [ ] Confirm UI and documentation continue to describe Strict Mode as a commitment aid, not an unbypassable Android lock.

### Notifications and local files

- [ ] With Notification access enabled, test a blocked app with notification capture both enabled and disabled. Confirm only matching blocked notifications are cancelled and locally retained when configured.
- [ ] Revoke Notification access and verify ordinary app use continues without a crash.
- [ ] Export and re-import JSON and website CSV. Also test malformed, oversized, invalid-domain, and newer-schema files; invalid imports should report an error without replacing the current state.
- [ ] Verify blocked notification content and other personal state remain on-device and that no cloud, account, telemetry, ad, or billing flow is introduced.

### Accessibility, lifecycle, and OEM behavior

- [ ] Test TalkBack, font scaling, portrait/landscape, compact screens, and keyboard/IME visibility for the main screen, profile editor, picker dialogs, pause overlay, and release dialogs.
- [ ] Background and restore the app, rotate the device, force-stop and reopen it, disable/re-enable Accessibility, and test battery optimization on at least one OEM device with aggressive background management.
- [ ] Confirm Android Settings, force-stop, uninstall, and permission revocation remain available and are not claimed to be blocked.

## Distribution review

Before publishing through an app store, review current store policy for the declared Accessibility service and `QUERY_ALL_PACKAGES` use. Passing CI or this checklist does not establish store-policy eligibility.
