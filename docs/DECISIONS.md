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

## Open questions

- Licence (MIT or GPL-3.0): decide before the first release.
