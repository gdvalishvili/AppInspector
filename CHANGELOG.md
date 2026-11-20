# Changelog

All notable changes to App Inspector. Versions follow `MAJOR.MINOR.PATCH`; `-alphaN` builds were test steps on the way to a release.

## [3.1.0] - unreleased
Background activity.

### Added
- **Background activity** page (⋮ menu), with three tabs:
  - **Now:** apps running a foreground service right now.
  - **7 days:** foreground service time and starts, plus background internet (Wi-Fi + mobile).
  - **Restrictions:** standby buckets and apps with unrestricted battery.
- **"Background" block** on the details screen: the same data and manifest capabilities (boot start, exact alarms, wake lock, foreground service types).
- ⚠ mark in **Cleanup** for unused apps that still run in the background.
- **Refresh button** (↻) on the main list.
- **Notification sound** switch for report notifications (default: silent). It uses a second notification channel.

### Changed
- The version is now set in `version.properties`. versionName and versionCode are computed by Gradle.
- Release APK name: `AppInspector-<version>-release.apk`.
- The list label "7 days" now reads "Used in 7 days".

## [3.0.0] - 2026-11-15
Phase 3: details, updates, cleanup, widget and logs.

### Added
- **More app info in the list** (alpha1): optional size (app + data) and manifest description columns, 7-day screen time, and "most used (7 days)" and "size" sorts.
- **App details screen** (alpha2): version code, target/min SDK, size, granted dangerous permissions, 30-day usage chart and the app's install/update/removal history. Opened by long-press. Includes an Uninstall button.
- **Updates page** (alpha3):
  - Play-installed apps, oldest update first, with 30+ / 90+ day groups.
  - Journal of updates in the last 7 days.
  - Links to each app's Play page.
- **Updates page improvements** (alpha3.1):
  - Four sort orders, remembered between launches.
  - A button that opens Play's "Manage apps & device" page directly.
  - Journal rows with app icons.
- **Cleanup page** (alpha4):
  - Groups: never opened (30+ days), unused 90+ / 180+ days, background apps, excluded.
  - Exclude list, multi-select, sorting by size / last use / name.
  - Batch uninstall with progress, a stop button and a summary of freed space.
- **Open button** on the details screen (alpha4.1): launches the app, work profile apps included.
- **Home screen widget** (alpha5): today's screen time and the top 3 apps with icons. It refreshes every 30 minutes, with the ↻ button and whenever the app is opened. Tapping it opens today's report.
- **Logs** (alpha6):
  - logcat (main + system + crash) with time filters, "errors only" and search.
  - Crash / ANR history from DropBox.
  - Manual snapshots (`.log.gz`) with auto delete.
  - Sharing as `.txt`.
  - Opened for the whole system or for a single app.
- **Log viewer improvements** (alpha6.1):
  - Fast scrollbar with a time bubble and jump-to-top/bottom buttons.
  - Collapsible search.
  - Configurable number of snapshots to keep (1–500, default 20).
- **Line selection** (alpha7):
  - Tap to select lines, long-press a second line to select the range in between.
  - Save, share or copy only the selected lines.
  - Bigger jump buttons, and an explicit Save button for the "files to keep" value.

### Changed
- App Inspector's own description added to the manifest (`android:description`).

### Permissions
- New: `REQUEST_DELETE_PACKAGES` (uninstall dialogs).
- New: `READ_LOGS` and `READ_DROPBOX_DATA`, granted once via adb.
- Still no `INTERNET` permission.

## [2.0.0] - 2026-11-13
Phase 2: history, usage recording and reports.

### Added
- **Category and install source** columns, and a filter by category (alpha2).
- **Install / update / removal journal**, with the exact uninstall time from `PACKAGE_FULLY_REMOVED`. A paused work profile is not treated as a removal.
- **Daily per-app usage recording** with WorkManager (alpha3):
  - Window-stack counting on `UsageEvents`, matching Digital Wellbeing.
  - The launcher is counted separately.
  - Up to 7 missed days are backfilled.
- **Report screen** (alpha4):
  - Day / week / month, bar chart, per-app time.
  - Comparison with the previous finished period.
  - Changes (installs, updates, removals) in the period.
- **Settings screen** (alpha5):
  - Silent daily / weekly / monthly report notifications at a chosen time (default 09:00).
  - "Show report now".
  - Data retention (default 2 years).
- **Release build**: R8 code shrinking, resource shrinking and release signing. Debug builds use the same key, so the app updates in place and keeps its data.
- **Unit tests** for periods, filtering and sorting, usage counting and the history diff.

### Changed
- Code refactored for testability.

## [1.1.0] - 2026-11-13
### Added
- Georgian / English UI, with a language choice on first launch and a top-bar toggle (ქარ / ENG).
- "Recently used" sort.
- App icon.

### Changed
- Filters reworked into single-choice chips: All / User / System / Work / Removed.

## [1.0.0] - 2026-11-13
First working version (MVP).

### Added
- List of all installed apps: icon, name, package, version, install, update and last-used dates.
- User / system / updated-system app types; archived apps recognised.
- Work profile apps (read-only, via `LauncherApps`).
- Search and sorting.
- Tap opens system App info.
