# App Inspector

A personal Android app that shows what is installed on the phone, how each app is used, and what happens to it over time: installs, updates, removals, crashes and logs.

Built for my own Google Pixel 9 Pro XL. **It is not published on Google Play.** The app works fully offline: it has **no INTERNET permission**, and all data stays on the device.

> Status: **v3.2.0** · Android 8.0+ (minSdk 26) · targetSdk 37 · Georgian / English UI

---

## Features

### App list
- All installed apps with icon, name, package, version, install, update and last-used dates, and 7-day screen time
- Filters: **All / User / System / Work profile / Removed**, plus a filter by app category
- Sorting: name, installed, updated, unused first, recently used, most used (7 days), size
- Optional columns: category, install source (Play, sideload…), size (app + data), manifest description
- Refresh button (↻) in the top bar
- Search; tap opens system App info, long-press opens the app's own **details** screen
- Work profile apps (read-only, via `LauncherApps`); archived apps are recognised

### App details
- Version code, target/min SDK, size, granted dangerous permissions
- 30-day usage chart and the install / update / removal history of the app
- Buttons: **Open**, App info, Play Store, Uninstall, **Logs**

### Usage and reports
- Daily per-app foreground time, recorded locally (WorkManager, once a day)
- Counting closely matches Digital Wellbeing (window-stack algorithm on `UsageEvents`; the launcher is shown on a separate line)
- **Report** screen: day / week / month, bar chart, per-app time, comparison with the previous period, changes in the period
- Daily / weekly / monthly report notifications at a chosen time, silent by default (optional sound)
- Configurable data retention (default: 2 years)

### History journal
- Install, update and removal events, with the exact uninstall time (`PACKAGE_FULLY_REMOVED`)
- A paused work profile is **not** treated as a removal

### Updates
- Play-installed apps sorted by the oldest update first, with 30+ / 90+ day groups and four sort orders
- Journal of updates in the last 7 days
- Tap opens the app's Play page; one button opens Play's **Manage apps & device** page

### Cleanup
- Groups: **never opened** (30+ days), **unused 90+ / 180+ days**, **background** apps, **excluded**
- Exclude list ("never touch"), multi-select, sorting by size / last use / name
- Batch uninstall: one system confirmation per app, progress, stop button, summary of freed space

### Logs
- **Log**: logcat (main + system + crash) with time filters (15 min / 1 h / all / range), "errors only", search, fast scrollbar with a time bubble, and jump-to-top/bottom buttons
- **Error history**: crashes, native crashes and ANRs from the system DropBox (survives reboots, usually kept for a few days)
- **Saved**: manual snapshots (`.log.gz`), with optional auto delete that keeps the last *N* files
- Line selection (tap, long-press range) to save, share or copy only the selected lines
- Opened from the menu (whole system) or from an app's details screen (only that app)

### Background activity
- **Now:** apps running a foreground service right now (from `UsageEvents`), with start time and service name
- **7 days:** foreground service time and number of starts per app, plus background internet (Wi-Fi + mobile, `NetworkStatsManager`)
- **Restrictions:** standby bucket per app (active / working set / frequent / rare / restricted) and apps with unrestricted battery
- Details screen: a "Background" block with the same data and manifest capabilities (starts on boot, exact alarms, wake lock, foreground service types)
- Cleanup: unused apps that still run in the background are marked with ⚠
- Short background jobs (WorkManager/JobScheduler) and battery use per app are not available to normal apps
## Privacy (3.2.0)
- Scans installed APKs on the phone (no internet permission, nothing leaves the device) and matches class names against the Exodus Privacy tracker list (`app/src/main/assets/trackers.json`).
- A tracker found in the code means the SDK is included, not proof that it sends data.
- Risk level = trackers (max 4 points) + sensitive permissions/access; "high" needs both.
- Background data history: table `daily_network` (day, package, background/foreground bytes), filled daily and when the Privacy screen opens. Retention is set in Settings (default 2 years).
- Tracker data: © Exodus Privacy, licensed under ODbL v1.0 (https://exodus-privacy.eu.org).

## Database
- Room version 2. Schema files are in `app/schemas/` and must be committed (needed for future migrations).

### Home screen widget
- Today's screen time and the top 3 apps with icons (Jetpack Glance)
- Refresh button; tapping the widget opens today's report

---

## Permissions

| Permission | Why | How it is granted |
|---|---|---|
| `QUERY_ALL_PACKAGES` | See every installed app (Android 11+ package visibility) | Automatic |
| `PACKAGE_USAGE_STATS` | Screen time, last used, app sizes, DropBox | Manually: Settings → Usage access → App Inspector |
| `POST_NOTIFICATIONS` | Report notifications | Asked in the app (Android 13+) |
| `REQUEST_DELETE_PACKAGES` | Uninstall dialogs (details, cleanup) | Automatic |
| `READ_LOGS` | System-wide logcat | Once, via adb (see below) |
| `READ_DROPBOX_DATA` | Crash / ANR history | Once, via adb (see below) |

There is no `INTERNET` permission.

### Granting log access (once)
```
adb shell pm grant com.gdvalishvili.appinspector android.permission.READ_LOGS
adb shell pm grant com.gdvalishvili.appinspector android.permission.READ_DROPBOX_DATA
adb shell am force-stop com.gdvalishvili.appinspector
```
The grants survive updates and reboots, and are lost when the app is uninstalled. Without a computer, the same commands can be run on the phone itself through Wireless debugging (for example with Shizuku + a shell app, or LADB).

Since Android 13 the system asks **"Allow one-time access"** each time the app reads the full log. Background log reading is blocked by Android, and the app does not try.

---

## Tech stack

| Area | Library / tool | Version |
|---|---|---|
| Language | Kotlin | 2.2.10 |
| Build | Android Gradle Plugin | 9.5.1 |
| UI | Jetpack Compose (BOM) + Material 3, dynamic color, edge-to-edge | 2026.02.01 |
| Database | Room + KSP | 2.8.4 / 2.3.13 |
| Settings | DataStore Preferences | 1.3.0 |
| Background | WorkManager | 2.12.0 |
| Widget | Jetpack Glance (appwidget) | 1.2.0 |
| Tests | JUnit 4 | 4.13.2 |

Architecture: **MVVM**, one Activity, simple state-based navigation (no navigation library), `StateFlow` + Compose.

```
app/src/main/java/com/gdvalishvili/appinspector/
├── MainActivity.kt        navigation, notification/widget entry points
├── data/                  repositories: installed apps, usage, history, reports, sizes, settings
│   ├── background/        foreground service sessions, standby buckets, background network, capabilities
│   └── logs/              logcat reader + parser, DropBox reader, snapshots, log settings
├── db/                    Room: entities, DAOs, database (schema exported to app/schemas)
├── model/                 AppEntry, Period, enums
├── notify/                report notifications
├── receiver/              package removal receiver
├── ui/                    screens: list, report, settings, details, updates, cleanup, logs, background (+ theme, components)
├── util/                  dates, icons, intents, language, sharing
├── widget/                Glance home screen widget
└── work/                  daily WorkManager job
```

Unit tests (`app/src/test`) cover periods, filtering and sorting, the usage counting algorithm, the history diff, cleanup rules, and the logcat / DropBox parsers.

---

## Building

### Version
The version lives in one place, `version.properties` in the project root:
```properties
MAJOR=3
MINOR=1
PATCH=0
```
Gradle computes `versionName = "3.1.0"` and `versionCode = MAJOR*10000 + MINOR*100 + PATCH` (30100). Nothing else is edited by hand. Raise the version on every release and add a short entry to `CHANGELOG.md`.

### Signing (keystore)
App Inspector has its **own** keystore (one app = one keystore). It is kept **outside** the project:
```
C:\Users\gdvalishvili\Documents\Hidden\Keystores\
```
The exact file path is `storeFile` in `keystore.properties` (project root, git-ignored):
```properties
storeFile=C:/Users/gdvalishvili/Documents/Hidden/Keystores/<file>.jks
storePassword=********
keyAlias=********
keyPassword=********
```
> ⚠️ **Back up both** the `.jks` file and `keystore.properties` (passwords) somewhere safe, outside this PC. Without the same key a new version cannot be installed over the old one, and the app data would be lost.

Release and debug builds use the same key (no `.debug` suffix), so **Run** from Android Studio updates the installed app and keeps its data.

### Build and install
```
# Windows (project root)
.\gradlew.bat assembleRelease
```
The release APK is copied automatically to:
```
app/build/outputs/named_apk/release/AppInspector-3.1.0-release.apk
```
Install over the old version:
```
adb install -r app/build/outputs/named_apk/release/AppInspector-3.1.0-release.apk
```
Data is kept as long as the signing key is the same and `versionCode` increases. Room schema changes need a migration (never destructive).

R8 (code and resource shrinking) is on for release builds, so **always test the release APK on the phone**, not only debug.

Google Play Protect may warn about a manually installed APK. This is expected for apps that are not on Play.

### Tests
```
.\gradlew.bat testDebugUnitTest
```
Or in Android Studio: right-click `app/src/test` → **Run 'Tests in …'**.

---

## Data and privacy
- Everything is stored in the app's private storage: the Room database, DataStore files and saved logs.
- `allowBackup="false"`: data is **not** copied to Google backup and is lost on uninstall. Export / import is a possible future feature.
- Logs can contain personal data. They leave the phone only when you use **Share**.

## Known limitations
- **Work profile:** no install date or usage time; the version and an approximate update date are read from the APK.
- **Private Space** is hidden from normal apps. A separate copy of App Inspector can be installed inside it.
- **Usage history** depends on Android's own retention. Days before the first launch can only be partly backfilled (up to 7 days).
- **Logcat** is a ring buffer in memory (a few MB, set in Developer options → Logger buffer sizes) and is cleared on reboot. The DropBox crash history lasts longer.
- Android gives no public API for "available updates" of other apps, so the Updates page shows how long ago each app was updated.

## Changelog
See [CHANGELOG.md](CHANGELOG.md).

## License
Personal project, no license. All rights reserved.
