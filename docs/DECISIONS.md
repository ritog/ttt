# Decisions

Dated log of decisions the spec left open (date, decision, why). Newest at the bottom.

## M0 — project skeleton

- **2026-11-16 — Kotlin is compiled by AGP 9's built-in Kotlin support in `:app`.** The Android Studio template already relies on it; adding the kotlin-android plugin would duplicate it.
- **2026-11-16 — `:core` targets JVM 11.** Matches `:app`'s `compileOptions`, so `:app` can depend on it without bytecode mismatches.
- **2026-11-16 — Navigation Compose 2.9.8, string routes.** Latest 2.9.x stable; chosen over 2.10 for safety with the template's Kotlin 2.2.10. String routes avoid the kotlinx-serialization plugin (not an allowed dependency).
- **2026-11-16 — Icons are local vector drawables (Material icon paths, Apache 2.0).** Material 3 no longer pulls in `material-icons-core`, and only five icons are needed; not worth a dependency.
- **2026-11-16 — Font: system default for now, not Inter.** Inter must be bundled as a font file; it is a one-file change in `Type.kt` (recipe in `CHANGING.md`). Revisit before M8.
- **2026-11-16 — No dynamic (wallpaper) colour.** The spec asks for one fixed calm palette.
- **2026-11-16 — Added `TttApplication`** (not in the SPEC 4.2 folder map) to own the `AppContainer`, so the container outlives activity recreation.
- **2026-11-16 — About screen shows the version from `BuildConfig`.** Enabled `buildConfig` in `app/build.gradle.kts`; simpler than querying `PackageManager`.
- **2026-11-16 — Menu screens (Log, Settings, About) show a back arrow, not the hamburger, and hide the bottom bar.** SPEC 9 puts the hamburger on the three main tabs; a back arrow is the expected way out of a secondary screen.
- **2026-11-16 — `android:allowBackup="false"` and data extraction rules exclude everything** (also device-to-device transfer), so the app's own backup (SPEC 8) is the only mechanism.
- **2026-11-16 — Named constants from the whole spec are already in `core/Constants.kt`**, so `:core` has real content and a test at M0, and later milestones reuse them.
- **2026-11-16 — versionName set to 0.1.0** until the first release.
- **2026-11-16 — Template test dependencies (espresso, compose ui-test) kept**, justified in `DEPENDENCIES.md`; they are test-only.

## M1 — core models and rules

- **2026-11-16 — Tag lists are always distinct and sorted alphabetically** (`normalizeTags`). The database has no tag order, so "the task's first tag" (used to tint blocks, SPEC 9.2) means the alphabetically first.
- **2026-11-16 — Quick add: invalid tag words stay in the title and are reported** (`rejectedTagWords`), e.g. `#c++`. The UI can show a short message; nothing is silently dropped or changed.
- **2026-11-16 — Quick add: a lone `#` is plain text; trailing `.,;:!?)]}"'` after a tag is dropped; spaces in the title are collapsed to one.** Simplest readings of the SPEC 6.1 edge cases.
- **2026-11-16 — The tag field accepts one leading `#`** (`normalizeTag("#book")` → `book`), because people will type it out of habit.
- **2026-11-16 — Title length counts Unicode code points**, so an emoji counts as one character towards the 200 limit.
- **2026-11-16 — Marking an already-done task done keeps its original `done_at`.**
- **2026-11-16 — Deleting a task while the timer is on it reports "Stop the timer first" even if the task also has sessions.** It is the message the user can act on.
- **2026-11-16 — Block times must be on 5-minute marks of local time** (validated, not only offered by the editor), so stored blocks always match what the editor can show.
- **2026-11-16 — A block may end exactly at the next midnight** ("maximum is the rest of that day"); one millisecond later is "crosses midnight".
- **2026-11-16 — Block problems are reported one at a time in a fixed order:** end not after start, off the grid, crosses midnight, too short, then overlap (earliest clashing block named).
- **2026-11-16 — Time ranges are half-open `[start, end)`**, so back-to-back blocks touch without overlapping.
- **2026-11-16 — Added `TimeRange.kt` and `TaskDeletion.kt`** (not in the SPEC 4.2 folder map): one concept per file.
- **2026-11-16 — The production clock is called `DeviceClock`** to avoid confusion with Android's `android.os.SystemClock`.

## Between M1 and M2 (requested by the author)

- **2026-11-16 — The Plan tab is labelled "Timeblock"** (a verb). Only the visible label changed (`tab_plan` in strings.xml); code, routes and the spec keep calling the screen "Plan", so file names still match SPEC 4.2. SPEC 9 has a note.
- **2026-11-16 — About shows "Made by Ritobrata Ghosh" and a link to github.com/ritog.** The link asks Android to open the browser; the app itself still has no INTERNET permission. If no browser exists, a short message says so.

## M2 — database and Todo

- **2026-11-16 — Room 2.8.5, not Room 3.0.** Room 3 drops the SupportSQLite APIs; the spec's backup/restore (`VACUUM INTO`, `PRAGMA integrity_check`) and `MigrationTestHelper` usage are simpler and better documented on 2.x. Revisit only if 2.x stops getting fixes.
- **2026-11-16 — KSP 2.3.10.** KSP 2.3.1 added AGP 9 built-in Kotlin support; KSP 2.3.x works with Kotlin 2.2.x. Known KSP issue #3053 (Room + `@Parcelize` on an entity) does not affect us: entities are not Parcelable.
- **2026-11-16 — Room Gradle plugin used** to export schemas to `app/schemas` and wire them into instrumented tests. Build tooling, not a runtime dependency.
- **2026-11-16 — The full v1 schema (all eight tables of SPEC 5.1) is created now**, so no migration is needed before the first release, even though block, entry, timer_state and setting are first used in M3–M5.
- **2026-11-16 — A missing timer_state row means IDLE**, instead of seeding the row on database creation. One less thing that can be wrong after a restore.
- **2026-11-16 — Phase and status are stored as text enum names** (`'RUNNING'`), so backups read clearly in sqlite3.
- **2026-11-16 — Tasks with tags are read with one query using a GROUP_CONCAT subquery**; tag names never contain commas, so splitting is safe. Sorting happens in Kotlin.
- **2026-11-16 — The timer "holds" a task whenever status is not IDLE** (running, paused, or awaiting the next phase); deleting the task is refused in all three cases.
- **2026-11-16 — Text field contents live in Compose `mutableStateOf` inside the ViewModels**, not in the `StateFlow` UI state. Android's guidance: text fields must update synchronously or the cursor can jump. Everything else follows SPEC 4.5 (one StateFlow per ViewModel).
- **2026-11-16 — The task editor sheet has its own `TaskEditorViewModel`.** Keeps both ViewModels under the 200-line rule; the sheet is part of the Todo screen and lives in `ui/todo/`.
- **2026-11-16 — The editor saves only on Save; Cancel or swiping the sheet away discards edits.** Predictable, and there is nothing half-saved. "Mark done" saves the edits first.
- **2026-11-16 — On Save, a half-typed tag in the tag field is added first** (or blocks saving with its error), because people forget to press Enter.
- **2026-11-16 — "Schedule a block" and "Start timer" are not shown in the editor yet.** They arrive with M3 and M4, so there are no dead buttons.
- **2026-11-16 — The Todo row's secondary line ("Next block…", "2h 15m worked") is deferred to M3/M5**, when blocks and sessions exist.
- **2026-11-16 — Many tags: the filter row scrolls sideways** instead of opening a sheet (SPEC 6.5 allows either). The Clear chip stays fixed at the start.
- **2026-11-16 — A selected filter tag that disappears (its last task deleted) is treated as no filter.**
- **2026-11-16 — Empty states:** first run says one sentence; a filter with no open tasks says so; "everything done" shows no extra line (the Done section says enough).
- **2026-11-16 — Invalid `#words` in quick add show a snackbar** ("Kept in the title, not valid tags: …").
- **2026-11-16 — TagFilterHolder is an activity-scoped ViewModel**, so it survives rotation but not process death (SPEC 4.5).
- **2026-11-16 — ViewModels are built with `viewModel { … }` factories** (no reflection), including TagFilterHolder.
- **2026-11-16 — Tag tint: hue from `String.hashCode` mod 360; saturation/lightness 45%/90% (light) and 30%/24% (dark).** Tune by eye later.

- **2026-11-16 — kotlinx-serialization-core raised to 1.9.0 with a Gradle `constraints` block.** First on-device run: all 8 repository tests passed, but `MigrationTest` crashed with `AbstractMethodError … typeParametersSerializers()`. Cause: Navigation brings serialization 1.7.x into the app, room-testing needs 1.8+, and AGP pins test dependencies to the app's versions. A constraint raises the floor without adding a new library.

## M3 — Timeblock (Plan) screen

- **2026-11-16 — Sessions column deferred to M5**, as the build order says; M3 draws only the blocks column.
- **2026-11-16 — Timeline scale: 72 dp per hour (1.2 dp per minute).** A 15-minute block is 18 dp tall: readable, but under the 48 dp touch target. Tapping short blocks is fiddly; revisit in the M8 accessibility pass (e.g. a list view of the day).
- **2026-11-16 — Blocks use a 6 dp corner radius, not 12 dp.** 12 dp makes short blocks look like pills.
- **2026-11-16 — On today the timeline opens an hour before now; other days open at 08:00.**
- **2026-11-16 — End time 00:00 means "midnight at the end of the day"** in the editor (BlockTimes). A block can end at midnight but can never end at the start of its own day, so there is no ambiguity.
- **2026-11-16 — The time picker allows any minute; the editor rounds to the nearest 5 minutes** (never wrapping past 23:55). The stock Material time picker has no step setting.
- **2026-11-16 — Changing the start keeps the block's length**, as calendars do. Duration chips set the end from the start and stop at midnight.
- **2026-11-16 — New blocks start at the next 5-minute mark on today (latest 23:45), otherwise 09:00, and last 60 minutes.** Constants in core/Constants.kt.
- **2026-11-16 — Deleting a block has no confirmation.** A block is cheap to re-create, and the spec says it can always be deleted.
- **2026-11-16 — The task picker lists open tasks only, newest first, at most 6;** search matches title text or the start of a tag. "Create …" uses the quick-add rules, so `#tags` work there too. The block editor's picker ignores the global tag filter (SPEC 6.5 only asks that of Track).
- **2026-11-16 — Times on screen are 24-hour ("14:05")**, matching the report's `hh:mm`. Following the phone's 12/24-hour setting can come later.
- **2026-11-16 — "Schedule a block" passes the task through an activity-scoped `ScreenRequests`, not a navigation argument.** Tab navigation restores each tab's saved state, which would drop a fresh argument. The editor saves pending edits first.
- **2026-11-16 — The Todo row's "Next block" line uses "now" from when the screen opened**, so a block that starts while the screen stays open is still shown until the screen is reopened. Cheap, and good enough for a hint.
- **2026-11-16 — The overlap check reads only the candidate's own local day**, inside the save transaction; blocks never cross midnight, so no other day can clash.
- **2026-11-16 — AppNav split into AppNav (the frame) and AppNavHost (routes and ViewModel factories)** to stay under the file-size rule.

## M4 — Timer

- **2026-11-16 — Exact alarms: declare `SCHEDULE_EXACT_ALARM`, not `USE_EXACT_ALARM`.** Current Android docs (differs from SPEC 10.1's wording, so recorded here per SPEC 0.6): on Android 14+ `SCHEDULE_EXACT_ALARM` is denied by default for new installs; `USE_EXACT_ALARM` is meant for alarm-clock and calendar apps. The app checks `canScheduleExactAlarms()`, uses `setExactAndAllowWhileIdle` when allowed and `setAndAllowWhileIdle` otherwise, and Track shows "The end alert may be a few minutes late" with an "Allow exact alarms" button.
- **2026-11-16 — Any event first ends a break that has already run out** (as if Evaluate had been sent), then applies the event. A late tap never acts on a break that is really over; still no chaining.
- **2026-11-16 — Going idle keeps the selected task** (timer_state.task_id) so Start is one tap next time; the long-break count and session fields reset as SPEC 7.3 says. Deleting that task is still allowed when idle (the "timer holds the task" check is status ≠ IDLE).
- **2026-11-16 — The task selection lives in timer_state** (ChangeTask while idle or waiting), so it survives app restarts and there is one source of truth.
- **2026-11-16 — The note prompt is kept through Start/Stop until saved or skipped;** a newer session replaces it. Skipping loses nothing.
- **2026-11-16 — The machine cannot know the new entry's id, so TimerRepository sets note_prompt_entry_id** right after inserting the entry, in the same transaction.
- **2026-11-16 — Evaluate on screen too:** when a break runs out while Track is visible, the screen sends Evaluate at once instead of waiting for the alarm (which may be late without exact alarms).
- **2026-11-16 — Countdown text rounds up, overtime rounds down** (25:00 at start, 00:00 only at the real end; +00:00 at the planned end).
- **2026-11-16 — Pause and Resume also work during breaks** (SPEC's Pause row is not limited to work).
- **2026-11-16 — Paused during overtime: primary button is Finish, secondary Resume and Stop.** SPEC 9.3 lists the running-overtime case; this is the paused counterpart.
- **2026-11-16 — The notification permission is requested on Start every time it is missing (Android 13+);** Android itself stops showing the prompt after two refusals. A quiet line on Track explains what notifications are for.
- **2026-11-16 — Notification icon: the Track tab's timer vector**, reused (monochrome, as Android requires).
- **2026-11-16 — Notification taps open MainActivity (launchMode singleTop) with an extra; AppNav switches to Track.** Simpler than deep links.
- **2026-11-16 — Pomodoro lengths are the defaults (25/5/15, 4) until Settings arrive in M5.**
- **2026-11-16 — "Today" on Track is fixed when the screen opens;** reopening after midnight starts the new day.
- **2026-11-16 — Corrupted timer_state text reads as IDLE** instead of crashing at start.
- **2026-11-16 — "Start timer" from a task or block selects the task on Track but does not start it** (SPEC 6.3: "opens the Track screen with that task selected").
- **2026-11-16 — TrackViewModel is ~230 lines**, a little over the 200-line guideline; it is one screen's actions grouped by section. Split if it grows in M5.

## M5 — Plan vs actual, Log, Settings

- **2026-11-16 — Continued-after-block picks the nearest earlier block** (latest end, or latest end of its chain) when several qualify. Only sessions counted as *continued* extend a chain; a session inside the block does not.
- **2026-11-16 — Worked-in-block uses integer milliseconds** (`overlap × worked ÷ wall-clock`, truncated per session). The error is under a millisecond per session; rounding to minutes happens only on screen and in the report.
- **2026-11-16 — Timeblock: the block label shows "14:00–16:00 · 1h 35m worked"** when the block is tall enough. That is the plan-vs-actual number on screen; continued time is left for the report.
- **2026-11-16 — Timeblock: sessions follow the tag filter too** (SPEC 6.5 names blocks; sessions are filtered the same way for consistency). A session running past midnight is drawn only up to midnight on the day it belongs to.
- **2026-11-16 — Log: tap a session to edit its note; "Delete session" is inside that dialog and asks to confirm.** One gesture instead of swipe + menu.
- **2026-11-16 — Log: "No tag" row** collects sessions of tasks without tags; the overlap note shows only when more than one tag is listed.
- **2026-11-16 — Log ranges are computed when chosen** (today, this week from Monday, this month) in the current zone.
- **2026-11-16 — Settings save on every tap (no Save button).** Numbers are clamped to their ranges (work 1–180, short 1–60, long 1–120, phases 1–12); invalid stored values read as defaults.
- **2026-11-16 — The timer reads settings on every event**, so a change applies from the next phase; a running phase keeps the length it started with (stored in timer_state).
- **2026-11-16 — Theme "system" is labelled "Same as the phone".** Status and navigation bar icons follow the chosen theme (`enableEdgeToEdge` with the theme's dark flag). The window background before the first frame still follows the phone (themes.xml); a brief flash on cold start with a forced theme is accepted.
- **2026-11-16 — Todo row: "Next block … · 2h 15m worked"** (all-time worked), either part when it applies; done tasks show only the worked part.
- **2026-11-16 — Deleting a session whose note card is still open on Track** leaves the card harmless: saving a note on a deleted session changes nothing.

## M6 — Backup, restore, auto-backup

- **2026-11-16 — Restore swaps the file at the next start instead of while running.** SPEC 8.3 says "replace the live database → recreate the database instance and the UI". Closing Room and replacing an open file under live ViewModels and Flows is fragile, so the checked copy is saved as `pending-restore.db` and the app restarts itself (new task + process exit); `TttApplication.onCreate` renames it over `ttt.db` before anything opens it. Same result for the user, and the live database is never half-replaced.
- **2026-11-16 — All checks and the migration run on a private copy before anything is replaced.** A refused or failed restore leaves the app exactly as it was, and the temporary files are deleted.
- **2026-11-16 — "Not a backup" is its own message.** A valid SQLite file without the `task` table (or with version 0) is refused as "not a ttt backup", separate from "damaged" (integrity check fails or Room cannot open it) and "could not be read". The empty-app check runs before the file picker as well as after it.
- **2026-11-16 — Timer state is cleared by deleting the row;** a missing row reads as idle everywhere, which is the same as resetting it to IDLE.
- **2026-11-16 — "Back up now" uses the system "Save as" picker only, no share sheet.** SPEC 8.2 allows either; the picker covers Drive, Files and USB already and needs no FileProvider. Add a share option later if wanted.
- **2026-11-16 — Auto-backup runs from `MainActivity.onStop`** (the app has one activity, so this is "the app went to the background"), on an app-wide coroutine scope so it finishes after the screen is gone. A failure keeps the old "last auto-backup" time, so it is retried next time and the stale date shows in Settings. Opening a system picker also triggers onStop; that is harmless because of the 24-hour rule.
- **2026-11-16 — Folder access through `DocumentsContract` directly**, not the `androidx.documentfile` library: three calls (create, list, delete) do not justify a dependency.
- **2026-11-16 — Auto-backup deletes only files whose names match the backup pattern exactly**, oldest first by name (the name sorts by date). Files the provider renamed (for example "… (1).db") are never touched.
- **2026-11-16 — "Keep last" range is 1–30, default 7**, shown only when a folder is chosen. "Stop auto-backup" releases the folder permission and removes the folder and last-backup settings.
- **2026-11-16 — The "Backup restored" message is shown after the restart** (a flag set by TttApplication), because a toast shown just before the process exits may never appear.
- **2026-11-16 — Backups use the MIME type `application/octet-stream`** so providers keep the `.db` name as given.

## M7 — Report export

- **2026-11-16 — Records belong to the range by their start**, as in the Log: a session from 23:50 to 00:20 on the last day of the range is in; one starting at 23:50 the day before is out.
- **2026-11-16 — Session plan status is matched over all records**, not only the range, so a session just inside the range that continued a block just outside it is still "Continued after block".
- **2026-11-16 — Minutes are rounded down, hours half up to one decimal.** Rounding down matches the app's screens, so the Log and the report never disagree by a minute. Differences are taken between the rounded minute cells, so every row adds up as shown.
- **2026-11-16 — Summary "Range" row says "All time" or "Selected days", followed by From/To as real dates.** Core only sees dates, so "This month" appears as its first and last day.
- **2026-11-16 — The report is in English, weekday names included** ("Mon"), like the rest of the app, so it does not change with the phone's language.
- **2026-11-16 — Tags sheet has a "(no tag)" row** when any task has no tags, as in the Log; it is always last. Summary's top tags leave it out.
- **2026-11-16 — Summary's top tasks and tags list only those with tracked time**; with none, the table says "None yet".
- **2026-11-16 — Tasks sheet order: open tasks oldest first, then done tasks most recently done first.** "First worked" and "Last worked" are the start dates of the first and last sessions.
- **2026-11-16 — Daily counts a day with a completed task as activity**, even with no session or block that day.
- **2026-11-16 — "Last 7 / 30 days" are rolling 24-hour periods back from the export time**, not calendar days.
- **2026-11-16 — Save picker only, no share sheet** (same as backup in M6). The file can be shared from Files or Drive afterwards.
- **2026-11-16 — The writer adds the hidden `_xlnm._FilterDatabase` name** for each data sheet, which Excel and LibreOffice use to show the autofilter, and writes numbers without exponent notation.
- **2026-11-16 — Report numbers live in Constants.kt** (top 5, 7 and 30 days, 32,000-character cells, 31-character sheet names).
- **2026-11-16 — Core was checked here by compiling with Gradle's bundled Kotlin 2.0.21 and running all 154 JVM tests; a sample export was read back with openpyxl** (dates, times, number formats, bold header, frozen pane, autofilter all as intended). The LibreOffice and Google Sheets check is in the manual checklist.

## Open questions

- Licence (MIT or GPL-3.0): decide before the first release.
