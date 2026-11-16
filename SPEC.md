# SPEC — Time blocking + time tracking + to-do (Android)

App name: **ttt** (time blocking, time tracking, to-do). Package and application ID: **`com.ritoghosh.ttt`** (the author owns `ritoghosh.com`).

The Android Studio project already exists (created from the "Empty Activity" template, Kotlin DSL, minSdk 30) at `~/AndroidStudioProjects/ttt`. Build on it; do not create a new project. Replace the template's sample `Greeting` code and the generated theme as M0 requires.

The display name lives in `strings.xml` (`app_name`). The package and application ID are fixed: they must never change after the first release, because Android treats a new ID as a different app.

This document is the single source of truth for building the app. It is written to be handed to a coding model (Claude Opus 5.5) and later read by a human maintainer who has no LLM. If code and this spec disagree, the spec wins until the spec is edited on purpose.

---

## 0. How to use this document (instructions for the builder)

1. Build in the order of **section 18 (Build order)**. Finish and verify one milestone before starting the next.
2. **Do not add features.** Section 2 lists what is out of scope. If something seems missing, write the question in `DECISIONS.md` and pick the simplest option that fits this spec.
3. Every decision that this spec leaves open goes into `DECISIONS.md` with a one-line reason (date, decision, why).
4. Follow **section 12 (Code rules)** for every file. Readability and maintainability beat performance and cleverness.
5. After each milestone: tests pass, every new file has its header comment, and the docs in section 14 are updated.
6. If a statement here about Android behavior (permissions, alarms, APIs) conflicts with the current official Android documentation, follow the documentation and record the difference in `DECISIONS.md`.

---

## 1. Purpose and principles

A small personal app with exactly three parts:

1. **To-do**: what I want to do.
2. **Time blocking**: when I will do it.
3. **Time tracking**: a pomodoro timer that records when I actually did it, with notes.

Plus tags across all three, a Log, a human-readable report export, and a backup/restore.

**Priority order for every decision (highest first):**

1. A human can read, understand and change the code without an LLM.
2. Correctness and data safety (never lose or corrupt the user's records).
3. Small and simple (few features, few dependencies, few concepts).
4. Fast. Performance is a soft budget (section 15), never a reason to make code harder to read.

**Product feel:** light, elegant, calm. Think Zerodha Varsity or the Lithium e-book reader, not ClickUp, banking apps or Amazon.

**Free to use:** open-source, no accounts, no ads, no analytics, **no network access at all** (the app does not declare the INTERNET permission).

---

## 2. Scope

### 2.1 In scope (v1)

- To-do list with flat tags and a done state.
- Time blocks: one block = one task + a time window on one day.
- Pomodoro timer with pause, tap-to-start-next-phase and per-session notes. Work past the planned length is tracked as overtime, and sessions stopped early keep their real (partial) length.
- Tags that flow from task to block to session (inherited, never entered twice) and one global tag filter.
- Log (history) with notes and time totals per tag.
- Settings: pomodoro lengths, theme, backup folder.
- Backup and restore (SQLite snapshot).
- Human-readable report export (.xlsx, six sheets).

### 2.2 Non-goals (the builder must NOT add these)

- No projects, folders, lists, priorities, assignees, due dates, custom fields, subtasks, dependencies.
- No recurring tasks or recurring blocks.
- No reminders other than the "pomodoro phase finished" notification.
- No accounts, cloud backend, live sync, sharing, teams, comments.
- No charts or analytics beyond the report's tables.
- No widgets, calendar integration, import wizards, AI features, gamification.
- No tag hierarchy, tag colors chosen by the user, or a tag management screen.
- No manual "add past session" form (possible later, see section 19).
- No foreground service. The running-timer notification is an ordinary notification (see 10.3).
- No web, desktop or iOS build (see section 16 for how to keep iOS cheap later).

### 2.3 Feature budget rule

Any new feature needs a written reason in `DECISIONS.md`. The default answer is no.

---

## 3. Platform and stack

| Item | Choice |
|---|---|
| Platform | Android only |
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 (restyled through one theme file) |
| Storage | SQLite through Room |
| minSdk | 30 (Android 11). Older versions are deliberately not supported, so `java.time` and SQLite's `VACUUM INTO` work with no workarounds |
| Gradle modules | Two: `:core` (plain Kotlin/JVM library) and `:app` (Android) |
| Package / applicationId / namespace | `com.ritoghosh.ttt` for `:app`; Kotlin package `com.ritoghosh.ttt.core` for `:core` |
| targetSdk | latest stable at build time |
| Build | Gradle with Kotlin DSL and a version catalog (`libs.versions.toml`) |
| Dependency injection | **None.** One hand-written `AppContainer` (section 4.4) |
| Async | Kotlin coroutines and `Flow` |

**Allowed dependencies (nothing else without a justification line in `DEPENDENCIES.md`):**
Kotlin stdlib and coroutines, AndroidX core-ktx, activity-compose, lifecycle (viewmodel + runtime for Compose), Navigation Compose, Compose BOM (ui, foundation, material3), Room (runtime, ktx, compiler via KSP). Test only: JUnit 4, kotlinx-coroutines-test, AndroidX test (for instrumented DB tests).

**Explicitly not used:** Hilt/Dagger/Koin, Retrofit/OkHttp (no network), Apache POI (too heavy), Moshi/Gson (no JSON needed), WorkManager (see 8.4), any analytics or crash-reporting SDK.

---

## 4. Architecture

### 4.1 Three layers, one direction

```
ui  →  data  →  core
```

- **`core`**: pure Kotlin. No Android imports, no Room imports. Models, rules, the timer state machine, report-row building, the XLSX writer. Fully unit-testable on the JVM. This is where the app's real logic lives.
- **`data`**: Room database, repositories, backup/restore, alarm scheduling. Converts between database rows and `core` models in one explicit file of mappers.
- **`ui`**: Compose screens and ViewModels. The UI never touches DAOs or the database directly; it talks to repositories.

A file may only import from its own layer or layers to its right in the diagram.

`core` is its **own Gradle module** (`:core`, a plain Kotlin/JVM library without the Android plugin). Android classes do not exist there, so the compiler itself enforces the rule; no extra check is needed. `data` and `ui` are packages inside the `:app` module, which depends on `:core`.

### 4.2 Folder layout

```
core/src/main/kotlin/com/ritoghosh/ttt/       Gradle module :core (pure Kotlin; its tests are in core/src/test)
  core/
    model/        Task.kt  Block.kt  Entry.kt  TimerState.kt  TagRules.kt
    time/         Clock.kt  DayRange.kt
    timer/        PomodoroSettings.kt  PomodoroMachine.kt
    plan/         BlockRules.kt            (validation, overlap)
    stats/        EntryBlockMatcher.kt  TagTotals.kt  WorkedTime.kt
    report/       ReportBuilder.kt  ReportTable.kt  ReportWriter.kt  XlsxWriter.kt
app/src/main/java/com/ritoghosh/ttt/          Gradle module :app (Android)
  data/
    db/           AppDatabase.kt  Entities.kt  Daos.kt  Mappers.kt  Migrations.kt
    repo/         TaskRepository.kt  BlockRepository.kt  EntryRepository.kt
                  TimerRepository.kt  SettingsRepository.kt
    backup/       BackupService.kt  RestoreService.kt  AutoBackup.kt
    alarm/        PhaseAlarmScheduler.kt  PhaseAlarmReceiver.kt  Notifications.kt
  ui/
    theme/        Theme.kt  Color.kt  Type.kt  Spacing.kt
    components/   TagChip.kt  TaskPicker.kt  TimeField.kt  EmptyState.kt  ...
    todo/         TodoScreen.kt  TodoViewModel.kt  TaskEditorSheet.kt
    plan/         PlanScreen.kt  PlanViewModel.kt  BlockEditorSheet.kt  DayTimeline.kt
    track/        TrackScreen.kt  TrackViewModel.kt
    log/          LogScreen.kt  LogViewModel.kt
    settings/     SettingsScreen.kt  SettingsViewModel.kt
    nav/          AppNav.kt
  AppContainer.kt
  MainActivity.kt
docs/             ARCHITECTURE.md  DATA.md  DEPENDENCIES.md  CHANGING.md  DECISIONS.md
SPEC.md  README.md
```

Rule of thumb: one concept per file, files around 200 lines at most, functions around 30 lines at most. Split rather than grow.

### 4.3 Time handling

- All instants are stored as **epoch milliseconds (UTC)** in the database.
- Day boundaries ("today", "this week") are computed with the **device's current time zone** at display/report time, using `java.time`.
- All code takes time from a `Clock` interface (`core/time/Clock.kt`) with `nowMs()` and `zone()`. Production uses the system clock; tests use a fake. **Never call `System.currentTimeMillis()` outside the production `Clock`.**
- Weeks start on Monday.
- **A session or a block belongs to the local day on which it starts.** A session from 23:50 to 00:15 counts entirely toward the first day, in the Log, the Plan screen and every report sheet.
- Known and accepted: if the user changes time zones, past records keep their instants but may fall on a different local day. Document this in `DATA.md`; do not try to fix it.

### 4.4 `AppContainer`

A plain class created once in the `Application` (or `MainActivity`) that builds the database, repositories, clock and scheduler, and exposes them. ViewModels get what they need through simple factories that read from the container. No reflection, no annotation-based injection. A new maintainer must be able to read `AppContainer.kt` and understand how the whole app is wired in one sitting.

### 4.5 State in the UI

- One `ViewModel` per screen exposing a single `StateFlow<XxxUiState>` (a data class) and plain functions for user actions (`onTaskChecked(id)`, `onStartPressed()`).
- Screens are stateless composables that take the state and callbacks.
- The **global tag filter** lives in a small activity-scoped holder (`TagFilterHolder`: `StateFlow<String?>`), shared by Todo, Plan, Track and Log. It resets when the app process restarts (it is not persisted).

---

## 5. Data model

Database name `ttt.db`. Room entities live in `data/db/Entities.kt`; the domain models in `core/model/` are separate Kotlin classes, converted by `Mappers.kt`. (Duplication is intentional: it keeps `core` free of Room.)

### 5.1 Tables

**task**

| column | type | notes |
|---|---|---|
| id | INTEGER PK autoincrement | |
| title | TEXT NOT NULL | trimmed, 1–200 chars, tags removed from it (see 6.1) |
| is_done | INTEGER NOT NULL | 0/1 |
| created_at | INTEGER NOT NULL | epoch ms |
| done_at | INTEGER NULL | epoch ms; set when marked done, cleared when un-done |
| note | TEXT NOT NULL DEFAULT '' | optional free text |

**tag**: `id` INTEGER PK, `name` TEXT NOT NULL UNIQUE (lowercase, see 6.1).

**task_tag**: `task_id`, `tag_id` (composite PK, both foreign keys with `ON DELETE CASCADE`).

**block**

| column | type | notes |
|---|---|---|
| id | INTEGER PK | |
| task_id | INTEGER NOT NULL | FK → task, ON DELETE CASCADE |
| start_at | INTEGER NOT NULL | epoch ms |
| end_at | INTEGER NOT NULL | epoch ms, greater than start_at |

**entry** (one finished or stopped work session)

| column | type | notes |
|---|---|---|
| id | INTEGER PK | |
| task_id | INTEGER NOT NULL | FK → task, ON DELETE RESTRICT (a task with sessions cannot be deleted, see 6.2) |
| started_at | INTEGER NOT NULL | wall-clock start of the session (epoch ms) |
| ended_at | INTEGER NOT NULL | wall-clock end |
| paused_ms | INTEGER NOT NULL | total pause time inside the session |
| note | TEXT NOT NULL DEFAULT '' | |

Worked time of an entry = `ended_at − started_at − paused_ms`.

**timer_state**: a single row with `id = 1` (see 7.2 for fields).

**setting**: `key` TEXT PK, `value` TEXT NOT NULL (see 9.3 for keys). Stored in the database so that backups include settings.

**meta**: `key`, `value`. Holds `created_at` and `app_version_at_creation`. The schema version is kept in SQLite's `user_version` (managed by Room).

Indexes: `block(start_at)`, `entry(started_at)`, `entry(task_id)`, `block(task_id)`, `task_tag(tag_id)`.

### 5.2 Rules that live in code (not only in the schema)

- Blocks may not overlap each other (6.3).
- Tags with no remaining tasks are deleted automatically when a task's tags change or the task is deleted.
- The schema is documented in `docs/DATA.md` in plain language, including the meaning of each column. **The schema is a contract**: changing it requires a Room migration, a test, and a `DATA.md` update.

---

## 6. Behavior

### 6.1 Tasks and tags

- Tags are **flat** and belong to **tasks only**. Blocks and entries inherit the tags of their task at display time.
- Tag format: lowercase letters, digits, `-` and `_`; 1–30 characters. Input is lowercased and invalid characters are rejected with a short message.
- **Quick add:** words that start with `#` in the title field are parsed as tags and removed from the title. Example: `Write outline #book #writing` → title `Write outline`, tags `book`, `writing`. The parsing function lives in `core/model/TagRules.kt` and is unit-tested (including edge cases: `#`, duplicate tags, trailing punctuation, `C#`-style text).
- The task editor sheet also shows tags as chips with an input to add or remove.
- Marking done sets `done_at`; un-checking clears it.

### 6.2 Deleting

- A task with **no sessions** can be deleted (its blocks are deleted with it). The editor asks for confirmation and states how many blocks will go.
- A task **with sessions** cannot be deleted (history is protected). The editor offers only "mark done".
- A task cannot be deleted while the timer is on it. The editor says "Stop the timer first".
- A block can always be deleted. A session (entry) can be deleted from the Log with confirmation.

### 6.3 Blocks

- A block belongs to exactly one task and lies within **one local day** (it cannot cross midnight in v1).
- Time granularity in the editor: 5 minutes. Quick duration chips: 30, 60, 90, 120 minutes.
- Minimum length 15 minutes; maximum is the rest of that day.
- **Blocks must not overlap.** Saving an overlapping block shows a message naming the clashing block and does not save.
- Blocks in the past may be created and edited (people plan loosely and fix later).
- From a block, the user can "Start timer on this task", which opens the Track screen with that task selected.

### 6.4 Sessions (entries)

- Created only by the timer (7.3). The user can edit an entry's **note** and delete the entry from the Log. Editing times is out of scope in v1.

### 6.5 The global tag filter

- One optional tag at a time. Chosen from a chip row (or a small sheet when there are many tags) at the top of Todo, Plan and Log, and as a task picker filter on Track.
- When set: Todo shows only tasks with that tag, Plan shows only blocks whose task has it, Log shows only matching entries and totals, Track's task picker lists only matching tasks.
- A small "clear" control is always visible when a filter is active. The filter is shared across screens.

### 6.6 Matching sessions to blocks (used by Log and the report)

Pure function in `core/stats/EntryBlockMatcher.kt`, heavily unit-tested.

For an entry E and the blocks of the **same task**:

1. **In block**: E overlaps a block. If it overlaps several, choose the one with the largest overlap.
2. **Continued after block**: E overlaps no block, but starts within `CONTINUATION_GAP_MINUTES = 30` after the end of a block of the same task, or within 30 minutes after the end of another entry already classified as continued for that block (chaining; process entries in time order).
3. **Unplanned**: anything else.

Derived numbers:

- `workedInBlockMs(block)` = for each entry of the same task, `overlap(entry window, block window) × (workedMs / wallClockMs of the entry)`, summed. (The factor spreads pauses evenly; this simplification is documented in code and in `DATA.md`.)
- `continuedAfterBlockMs(block)` = worked time of entries classified as continued for that block.

All math is done in milliseconds; **round to whole minutes only when writing report cells or UI labels**, so totals never drift.

---

## 7. Timer (pomodoro)

### 7.1 How the timer behaves (in words)

- A **work phase** counts down to its planned length, then **keeps counting as overtime** until the user taps Finish. Overtime is real work and is recorded.
- A session **stopped early** is recorded with its real, partial length.
- A **break** ends on its own at its planned length. Breaks are never recorded.
- **Nothing starts without a tap.** After a work phase the break waits; after a break the next work phase waits.

### 7.2 Persisted state (`timer_state`, single row)

| field | meaning |
|---|---|
| phase | `NONE`, `WORK`, `SHORT_BREAK`, `LONG_BREAK` |
| status | `IDLE`, `RUNNING`, `PAUSED`, `AWAITING_START` |
| task_id | task being worked on (kept through breaks), nullable |
| phase_length_ms | planned length of the current phase |
| accumulated_ms | elapsed time in this phase before the current run segment |
| running_since_ms | epoch ms when the current run segment began; null unless RUNNING |
| paused_since_ms | epoch ms when the current pause began; null unless PAUSED |
| session_started_at_ms | wall-clock start of the current WORK phase; null otherwise |
| planned_end_reached_at_ms | the moment a WORK phase reached its planned length; null until that is known (see below) |
| completed_work_phases | work phases finished in the current unbroken run of pomodoros |
| note_prompt_entry_id | entry waiting for a note prompt, nullable |

Time is **never stored or counted by a ticking counter**. It is computed from timestamps:

- `elapsed(now) = accumulated + (RUNNING ? now − running_since : 0)`
- `remaining = phase_length − elapsed` (work and break phases, while positive)
- `overtime = elapsed − phase_length` (work phases only, once positive)

This keeps the timer correct after the app is backgrounded or killed, or the phone sleeps. The UI refreshes its display about once per second only while visible.

**`planned_end_reached_at_ms`.** A work phase always crosses its planned length during a run segment, so the moment is `running_since + (phase_length − accumulated)`. Whenever a run segment ends (Pause, Finish or Stop) and `elapsed ≥ phase_length` and the field is still null, store that value. It is only used by the "keep planned time only" choice below.

### 7.3 State machine (`core/timer/PomodoroMachine.kt`)

Pure functions: `(state, event, now, settings) → TransitionResult(newState, entryToSave?, alarmAction)`. No Android, no database. Unit tests cover every row below.

| Event | From | To | Effects |
|---|---|---|---|
| `Start(task)` | IDLE | WORK / RUNNING | `running_since = now`, `session_started_at = now`, `accumulated = 0`, length = work length; schedule alarm at `now + length` |
| `Pause` | RUNNING | PAUSED | `accumulated += now − running_since`, `running_since = null`, `paused_since = now`; cancel alarm |
| `Resume` | PAUSED | RUNNING | `running_since = now`, `paused_since = null`; if `remaining > 0`, schedule alarm at `now + remaining` |
| `Evaluate(now)` | break RUNNING with `elapsed ≥ length` | AWAITING_START (WORK queued, same task) | called on app open/resume and by the alarm |
| `Evaluate(now)` | anything else | unchanged | a WORK phase past its planned length simply continues as overtime |
| `Finish(keepOvertime)` | WORK RUNNING/PAUSED with `elapsed ≥ length` | AWAITING_START (break queued) | save an entry (rule below); `completed_work_phases += 1`; queue `LONG_BREAK` if `completed_work_phases % workPhasesBeforeLongBreak == 0`, else `SHORT_BREAK`; set `note_prompt_entry_id`; cancel alarm |
| `Stop(keepOvertime)` | WORK RUNNING/PAUSED | IDLE | if worked time ≥ 60 s, save an entry (rule below); otherwise discard; cancel alarm |
| `Stop` | break phase or AWAITING_START | IDLE | cancel alarm |
| `StartNext` | AWAITING_START | RUNNING | begins the queued phase; if it is WORK, `session_started_at = now`; schedule alarm |
| `SkipBreak` | AWAITING_START (break queued) | AWAITING_START (WORK queued) | none |
| `ChangeTask(task)` | IDLE or AWAITING_START | same | only allowed when not mid-WORK |

**Whenever the state becomes IDLE**, reset `completed_work_phases = 0` and clear the session fields. (The long-break count belongs to one unbroken run of pomodoros; it must not carry over to the next day.)

**The entry saved by Finish and Stop (one rule for both):**

- `end` = `now` if RUNNING, or `paused_since` if PAUSED (a pause left open at the end is not part of the session).
- `worked` = `elapsed`.
- Entry: `started_at = session_started_at`, `ended_at = end`, `paused_ms = (end − started_at) − worked`, empty note.

**Long overtime (a forgotten timer).** If `overtime > LONG_OVERTIME_MINUTES` (60) when the user taps Finish or Stop, the UI first asks: "Keep all 3h 35m" or "Keep the planned 25m only". The answer is the `keepOvertime` flag. Below that threshold the UI does not ask and passes `true`. With `keepOvertime = false` the entry is cut back to the plan: `ended_at = planned_end_reached_at`, `worked = phase_length`, `paused_ms = (ended_at − started_at) − phase_length`. The app never discards time on its own.

**Never chain phases.** If the phone was off for hours during a break, only that break is completed. The user taps to continue.

**Atomicity:** saving the entry and updating `timer_state` happen in **one database transaction**.

**Note prompt:** after a session is saved (finished or stopped), the Track screen shows a small card "Note for this session" with a text field and Save/Skip. The entry already exists, so skipping loses nothing; saving updates its note; either clears `note_prompt_entry_id`.

### 7.4 No block required

The timer can start from any task with or without a block. If a block for that task exists today, Track shows it as a quiet "Planned today 14:00–16:00" line; it is information, not a constraint. Overrunning a block is normal and needs no special handling.

---

## 8. Backup and restore

### 8.1 Backup file

- A consistent **snapshot of the SQLite database**, created with `VACUUM INTO '<temp path>'` (safe while the app is running), then copied to the destination the user chooses.
- File name: `ttt-backup-YYYY-MM-DD-HHmm.db`.
- Contains everything, including settings and timer state. Format is platform-neutral SQLite (epoch ms, plain types), documented in `DATA.md`.

### 8.2 Manual backup

Settings → "Back up now" → system file picker (`CreateDocument`) or the share sheet. Shows a success or error message.

### 8.3 Restore

Settings → "Restore backup" (and offered on the empty Todo screen as a small text link).

- **Only allowed when the app has no tasks, blocks or entries.** Otherwise show: "Restore works on an empty app. To restore here, clear the app's storage in Android settings first." (No merge logic in v1.)
- Steps: user picks the file → copy to a temp file in the app's private storage → verify: it opens as SQLite, `PRAGMA integrity_check` returns `ok`, `user_version` is not newer than this app's schema (otherwise refuse with "made by a newer version of the app") → open it with Room so older backups are **migrated by the normal migrations** → replace the live database → recreate the database instance and the UI.
- Any failure leaves the current (empty) database untouched and shows a clear message.
- After a successful restore, clear what belongs to the old device: reset `timer_state` to IDLE (a timer that was running there must not create a session here), and delete the `auto_backup_folder_uri` and `last_auto_backup_at` settings (the folder permission does not carry over to another phone).

### 8.4 Auto-backup (simple, no extra libraries)

- Settings: "Auto-backup folder" (chosen once through the folder picker; keep the persisted permission) and "Keep last N" (default 7).
- When the app moves to the background and the last auto-backup is **older than 24 hours**, write a snapshot into that folder and delete the oldest files that match the backup name pattern beyond N.
- This lets Syncthing, Drive, Dropbox or similar carry the backups off the phone. There is no WorkManager and no background service.
- Set `android:allowBackup="false"` in the manifest in v1, so there is exactly one backup mechanism.

---

## 9. Screens

Navigation: **three main tabs: Todo, Plan, Track** (bottom bar). The Plan tab is labelled **"Timeblock"** in the app (a verb); code and this spec keep calling the screen "Plan". A **hamburger menu** (top-left, available on all three) opens a simple drawer or menu with **Log**, **Settings** and **About**.

### 9.1 Todo

- An always-visible "Add a task…" field at the top (tags via `#tag`). Enter adds the task and keeps focus.
- Below: tag filter chips (only tags that exist), then open tasks, **newest first**. A "Done" section below, collapsed by default, newest completion first.
- Each row: checkbox, title, tag chips, and a quiet secondary line (e.g. "Next block Sat 14:00" or "2h 15m worked") when relevant.
- Tap a row → **task editor sheet**: title, tags, note, buttons "Schedule a block" and "Start timer", delete (per 6.2).
- Empty state: one sentence and the add field.

### 9.2 Plan (time blocking)

- A **day view**: a vertical hour timeline. Day navigation: previous/next arrows, a "Today" button, and a date label; horizontal swipe is optional.
- **Two narrow columns**: left, the planned **blocks** (labelled with task title, tinted by the task's first tag colour); right, thin bars for the **actual sessions** of that day. This gives plan vs actual at a glance.
- A "+" button opens the **block editor sheet**: pick a task (search, recent tasks, or create a new one inline), date, start, end (or a duration chip). Validation messages per 6.3.
- Tap a block → edit/delete or "Start timer".
- The current time is shown as a thin line on today.

### 9.3 Track (timer)

Idle:
- Task picker (recent tasks, search, create new inline; respects the tag filter).
- Quiet line showing today's next block, if any.
- Primary button **Start**.

Running / paused / awaiting:
- Phase label (Work / Short break / Long break) in small caps.
- **Task title** large above the time.
- **Time** in a large tabular-figure style: a countdown to the planned length, then overtime counting up, shown as `+12:34` in the accent colour.
- A thin progress line (full once the planned length is reached).
- Primary button by state: Pause / Resume while counting down, **Finish** during overtime, Start next when awaiting. Secondary: Pause (during overtime), Stop (with confirmation if worked ≥ 1 minute), Skip break.
- Finish or Stop with more than 60 minutes of overtime first shows the two-choice sheet from 7.3.
- The note card (7.3) appears here after a session ends.
- A small row "Today: ● ● ● 3 sessions · 1h 15m".

### 9.4 Log (in the menu)

- Range chips: Today, This week, This month. Under them, **time per tag** for the range (tag, total).
- Then entries grouped by day, newest day first; each day header shows its total. Each entry: time range, task, worked minutes, note. Tap to edit the note; swipe or menu to delete (confirmation).
- Respects the global tag filter.

### 9.5 Settings (in the menu)

Pomodoro: work minutes, short break, long break, work phases before a long break. Theme (system / light / dark). Backup: Back up now, Restore, Auto-backup folder, Keep last N. Report: "Export report". Nothing else.

Setting keys (stored in the `setting` table): `work_minutes` (25), `short_break_minutes` (5), `long_break_minutes` (15), `work_phases_before_long_break` (4), `theme` (`system`), `auto_backup_folder_uri`, `auto_backup_keep` (7), `last_auto_backup_at`.

---

## 10. Notifications and permissions

### 10.1 Declared permissions

- `POST_NOTIFICATIONS` (runtime request on Android 13+, asked the first time a timer starts, with a one-line explanation).
- An exact-alarm permission as required by the current Android version for timer-type apps. **Verify the current rules** in the Android docs (`SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM`). If exact alarms are unavailable, fall back to a normal alarm and tell the user the end notification may be a little late.
- **No INTERNET, no storage permissions** (files go through the system picker).

### 10.2 Planned-end alert

When a phase starts or resumes before its planned end, schedule an alarm for that moment (`PhaseAlarmScheduler`). When it fires, `PhaseAlarmReceiver`:

- for a **break**: calls the machine's `Evaluate`, saves the result, and posts "Break over";
- for a **work phase**: changes nothing in the timer (work continues as overtime) and posts "Planned time reached: <task>. Still tracking."

Tapping the alert opens the Track screen. Alerts use their own notification channel, default importance with sound.

On app start, always call `Evaluate`, and **re-schedule** the alarm if a phase is RUNNING before its planned end (alarms are lost on reboot; a reboot mid-session is rare and accepted).

### 10.3 Running-timer notification (no foreground service)

While a phase is running or paused, show one **ordinary ongoing notification** so the time is visible outside the app. The system draws the moving time itself (the notification's built-in chronometer), so the app runs no code while it is displayed.

- RUNNING before the planned end: chronometer counting **down** to the planned end.
- RUNNING in overtime: chronometer counting **up** from the planned end (the alarm receiver makes this switch when it fires).
- PAUSED: plain text "Paused", no chronometer.
- IDLE or AWAITING_START: the notification is removed.

It uses a second, silent, low-importance channel. Tapping opens the Track screen. It has no action buttons in v1 (pause and stop happen in the app). One file, `Notifications.kt`, builds every notification from the timer state; it is called after each state change and on app start. If the user refuses the notification permission, the timer works exactly the same without it.

Do **not** add a foreground service. If the notification proves unreliable on some phone, record what was observed in `DECISIONS.md` before changing the approach.

---

## 11. Report export (human-readable)

Settings → "Export report" → choose a date range (default **All time**; options: This month, Custom) → the app builds one `.xlsx` and offers it through the system save dialog or share sheet. It is **one-way and read-only**: nothing imports it back.

### 11.1 Format rules

- One `.xlsx` workbook, **six sheets** in this order: **Summary, Tasks, Blocks, Sessions, Daily, Tags**.
- **No IDs anywhere.** Real names, local times, plain words.
- Dates are real spreadsheet dates (format `yyyy-mm-dd`), times are real times (`hh:mm`), minutes are plain whole numbers (so they can be summed), tags are comma-separated text in one cell.
- Every data sheet: bold header row, frozen header row, autofilter on the header, sensible column widths (computed from content, capped).
- Values only, **no formulas** (works identically in Google Sheets, LibreOffice, Excel).
- Dates are shown in the device's time zone at export time.
- The Summary states the range and the generation time so a partial report is never mistaken for the whole.

### 11.2 Sheets

**Summary** (key/value rows, then two small tables)
- Generated at; range covered.
- Tasks: open count, done count.
- Time tracked (hours, 1 decimal); time planned in range (hours); planned minus tracked.
- Upcoming planned time (blocks starting after now), in hours.
- Number of sessions.
- This week vs last week tracked hours.
- Table: **Top 5 tags** by tracked time (tag, hours). Table: **Top 5 tasks** by tracked time (task, hours).
- A note: "Tag totals overlap; a task with two tags counts in both."

**Tasks** (every task, including never-worked and done ones; open tasks first, then done)
Task | Tags | Status (Open/Done) | Created | Done on | Blocks planned | Sessions | Minutes worked | First worked | Last worked | Next block | Note
(Counts and minutes are within the range; the others are not range-limited.)

**Blocks** (past, current and future; sorted by start, ascending)
Date | Weekday | Start | End | Planned min | Task | Tags | Status (Upcoming / In progress / Past) | Worked in window (min) | Difference (min; worked − planned) | Continued after end (min) | Task done? (Yes/No)

**Sessions**
Date | Start | End | Worked min | Paused min | Task | Tags | Note | Plan status (In block / Continued after block / Unplanned) | Block (e.g. `14:00–16:00`, or empty)

**Daily** (one row per day with any activity or any block)
Date | Weekday | Planned min | Tracked min | Difference | Sessions | Tasks worked on (distinct) | Tasks completed

**Tags**
Tag | Open tasks | Done tasks | Minutes worked (range) | Last 7 days (min) | Last 30 days (min) | Upcoming planned (min)
(The 7- and 30-day columns are relative to the generation time, independent of the range.)

### 11.3 Implementation

- `core/report/ReportBuilder.kt`: takes the domain data (tasks with tags, blocks, entries), the range, the clock, and returns plain `ReportTable` objects (title, header, rows of typed cells: `Text`, `Number`, `Date`, `Time`, `DateTime`). Unit-tested with small, readable fixtures. **No file or Android code here.**
- `core/report/ReportWriter.kt`: a tiny interface `write(tables, output stream)`.
- `core/report/XlsxWriter.kt`: the implementation. About 150–250 lines using only `java.util.zip` and string building. Requirements:
  - Parts: `[Content_Types].xml`, `_rels/.rels`, `xl/workbook.xml`, `xl/_rels/workbook.xml.rels`, `xl/styles.xml` (bold header, date, time, date-time, integer formats), one `xl/worksheets/sheetN.xml` per table.
  - Strings as **inline strings** (no shared-strings part) to keep it simple.
  - Escape XML special characters and strip illegal control characters; truncate cells at 32,000 characters; sheet names at most 31 characters.
  - Dates and times written as Excel serial numbers with a style.
  - Frozen first row, autofilter, column widths.
  - Test: build a workbook, unzip it in the test, check every XML part is well-formed and cell values match. Also add a **manual check** in `docs/CHANGING.md`: open a sample export in LibreOffice and Google Sheets.
- Keep the `ReportWriter` interface so a zip-of-CSVs writer can be swapped in if a viewer ever misbehaves. (If a CSV writer is ever added, prefix cells that begin with `=`, `+`, `-` or `@` with a single quote to avoid formula injection.)

---

## 12. Code rules (for every file)

1. **Header comment on every file**: what it is for (one or two sentences), what it depends on, and what it must **not** do. Example:
   ```kotlin
   /**
    * PomodoroMachine — the timer's rules, as pure functions.
    *
    * Depends on: core/model, core/timer only.
    * Must not: touch Android, the database or the system clock (use the Clock passed in).
    */
   ```
2. **Comment the why**, not the what. Explain any non-obvious rule, simplification or trade-off right where it happens. Public functions get KDoc.
3. **Boring code**: plain functions, data classes, sealed classes for events and states. No reflection, no custom DSLs, no clever generics, no operator overloading, no global mutable singletons.
4. **Names**: full words (`workedMinutesInBlock`, not `calcMw`). No abbreviations except widely known ones (`id`, `ms`, `ui`).
5. **Size limits**: about 200 lines per file, about 30 per function. Split by concept.
6. **No `!!`**; handle null explicitly. No swallowed exceptions: either handle them or surface a message.
7. Constants are named and commented (`MIN_ENTRY_MS`, `CONTINUATION_GAP_MINUTES`), in one place.
8. Strings shown to the user live in `strings.xml`.
9. **Performance only after measuring.** An optimisation that makes code harder to read needs a measured problem and a comment stating the number.
10. Formatting: official Kotlin style (ktlint-compatible); consistent import ordering.
11. Tests are written as readable examples (given / when / then, descriptive names) because they double as documentation of the rules.

---

## 13. Testing

**JVM unit tests (required; run without a device):**
- `PomodoroMachine`: every transition in 7.3, pause/resume arithmetic, overtime (including pause and resume during overtime), `planned_end_reached_at`, Finish and Stop with `keepOvertime` true and false, finishing while paused, break completion after a long absence, no phase chaining, long-break cadence and its reset on IDLE, discard under 60 s, entry fields (`paused_ms`).
- `TagRules`: parsing, normalising, rejecting.
- `BlockRules`: overlap, same-day, minimum length.
- `EntryBlockMatcher` and `WorkedTime`: in block, continued (including chains), unplanned, pause apportioning.
- `ReportBuilder`: each sheet's rows from a small fixture, range filtering, rounding at the edge only.
- `XlsxWriter`: well-formed parts, values, escaping.

**Instrumented tests (on a device or emulator; keep few and focused):**
- Room: DAOs and migrations (`MigrationTestHelper`), foreign-key behaviour (RESTRICT on tasks with entries).
- Backup → restore round trip into an empty database; refusal of a newer-schema file; refusal when data exists.

**Manual checklist** in `docs/CHANGING.md`: timer survives app kill, phone sleep and reboot (alarm re-schedule on open), report opens in LibreOffice and Sheets, dark/light themes, 200% font scale, TalkBack basics.

---

## 14. Documentation deliverables (part of the product)

- `README.md`: what the app is, screenshots, how to build and install, licence.
- `docs/ARCHITECTURE.md`: one page: layers, folder map, data flow of the three key paths (add a task, run a pomodoro, export a report).
- `docs/DATA.md`: every table and column in plain language, the epoch-ms and time-zone rules, the backup format, how to open a backup with the `sqlite3` command-line tool for inspection.
- `docs/DEPENDENCIES.md`: each dependency with a one-line reason.
- `docs/CHANGING.md`: recipes: "change a pomodoro default", "add a column (migration steps)", "add a sheet to the report", "change the colour palette", plus the manual test checklist.
- `docs/DECISIONS.md`: dated log of decisions made while building.

---

## 15. Design brief

### 15.1 Feel

Calm, light and confident. Typography does the work; there are few boxes and no decoration. Nothing should look like a 90s/00s enterprise form or a bloated super-app.

### 15.2 Rules

- **One typeface** (Inter, variable weight) bundled with the app; sizes limited to a small scale (about 12 / 14 / 16 / 20 / 28 / 56 for the timer). Use **tabular numerals** for the countdown and times. If bundle size becomes a concern, fall back to the system font and note it in `DECISIONS.md`.
- **Colour**: a warm neutral background, near-black text, hairline dividers, and **one accent colour** used only for the running timer, primary buttons and the current-time line. Define light and dark palettes as named tokens in `Color.kt` (suggested start: background `#FAFAF8` / `#121212`, text `#1A1A1A` / `#ECECEC`, accent a deep teal). Tune by eye, keep contrast accessible (WCAG AA for text).
- **Tag colours** are derived automatically from the tag name (a hash chooses a hue; saturation and lightness fixed per theme). Users never pick colours.
- **Spacing** scale: 4, 8, 12, 16, 24, 32 dp, defined once in `Spacing.kt`. Generous whitespace. Corner radius 12 dp for sheets and chips.
- **No** gradients, drop shadows, illustrations, splash animations, onboarding carousels, banners or badges.
- **Motion**: only short functional transitions (about 150 ms): sheet in/out, tab change, progress line.
- **Components**: use stock Material 3 components restyled through the theme. Build a custom component only when no stock one fits (the day timeline is one such case).
- **Accessibility**: touch targets at least 48 dp, content descriptions on icon buttons, correct behaviour at large font scale, state never conveyed by colour alone.
- **Copy**: short, plain, no exclamation marks, no jargon. Empty states are one sentence.

### 15.3 Screen sketches (intent, not pixel specs)

- **Track**: lots of empty space; task title, then a big countdown, then one prominent button; secondary actions small and quiet.
- **Plan**: a clean hour grid with very light lines; blocks as softly tinted rounded rectangles with the title; session bars thin and neutral.
- **Todo**: a quiet list, no row borders, 56 dp rows, tags as small lowercase text chips.

---

## 16. Keeping iOS cheap later (a rewrite in Swift is planned only if needed)

No code is shared, but these habits make a rewrite mostly mechanical:

1. `core` is pure Kotlin with no platform imports, so its logic and tests can be read and ported function by function.
2. The timer is timestamp-based with a scheduled end notification, a model that works on iOS too.
3. The database schema and the backup format are documented and platform-neutral (`DATA.md`).
4. The tests and this spec describe the behavior to reproduce.

---

## 17. Distribution

- Open-source repository. Licence to be chosen before first release (MIT or GPL-3.0 both work with F-Droid); record in `DECISIONS.md`.
- Signed release APK attached to GitHub Releases. Keep the signing key safe and backed up; losing it prevents updates.
- F-Droid listing is optional later; the dependency list (AndroidX only, no proprietary SDKs) is compatible. Play Store is optional.
- No telemetry of any kind.

---

## 18. Build order

Each milestone ends with: tests green, file headers present, docs updated, a short note in `DECISIONS.md`, and a stop for human review.

| # | Milestone | Done when |
|---|---|---|
| M0 | Project skeleton: Gradle + version catalog, theme tokens, empty three-tab navigation with the hamburger menu, `AppContainer`, the two Gradle modules (`:core`, `:app`), docs skeletons | app runs; `:core` builds and tests without Android |
| M1 | `core` models, `TagRules`, `BlockRules`, `Clock`, with unit tests | tests cover all rules in 6.1–6.3 |
| M2 | Room database, mappers, repositories, migrations setup; **Todo** screen end to end (add, check, edit, delete rules, tags, filter) | tasks and tags persist; delete rules (6.2) enforced |
| M3 | **Plan** screen: block editor, day timeline, overlap rules | blocks persist; overlaps rejected with a clear message |
| M4 | `PomodoroMachine` first (tests first), then `TimerRepository`, **Track** screen, alarm + notification, note prompt, recovery on open | all 7.3 tests green; timer survives kill/sleep (manual) |
| M5 | `EntryBlockMatcher`, Plan's session column, **Log**, **Settings** (pomodoro lengths, theme) | plan vs actual visible; Log totals per tag correct |
| M6 | Backup, restore, auto-backup | round-trip test passes; restore refuses non-empty and newer files |
| M7 | `ReportBuilder`, `XlsxWriter`, export UI | report opens correctly in LibreOffice and Google Sheets; all six sheets match 11.2 |
| M8 | Polish: empty states, accessibility pass, dark theme pass, performance sanity check (section below), README with screenshots, release build | checklist in `CHANGING.md` fully ticked |

### Performance sanity check (soft budget, M8)

Generate a test database with about 20,000 sessions and 5,000 tasks. Todo, Plan, Log and the report export should all feel instant to a person (screens under about half a second; report under a few seconds). Cold start to a usable Todo list should feel under about one second on a mid-range phone. If a target is missed, **measure first**, fix the cheapest cause, and comment the reason (rule 9, section 12).

---

## 19. Assumptions and later possibilities

**Assumptions made in this spec (change by editing the spec):**
- Open tasks are ordered newest first; there is no manual ordering.
- Only one tag filter at a time.
- A pomodoro always needs a task (a new one can be created inline in one step).
- The report's default range is all time; no extra Notes sheet.
- The app asks before keeping overtime only when it exceeds 60 minutes (`LONG_OVERTIME_MINUTES`).
- A session that crosses midnight counts toward the day it started.
- Android 11 is the oldest supported version.

**Possible later additions (not v1; each needs a `DECISIONS.md` entry):**
- Manual "add a past session" form.
- Pause/stop buttons on the running-timer notification.
- Tag rename.
- CSV-zip report as an alternative writer.
- Multi-day or midnight-crossing blocks.
- iOS (Swift rewrite, see section 16).
