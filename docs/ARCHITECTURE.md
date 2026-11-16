# Architecture

One page on how the app is put together. The full rules are in `SPEC.md` section 4.

## Layers

```
ui  →  data  →  core
```

A file may import only from its own layer or layers to its right.

| Layer | Where | What it holds |
|---|---|---|
| `core` | Gradle module `:core` (plain Kotlin/JVM, no Android plugin) | Models, rules, timer state machine, report building, XLSX writer. All the real logic. Unit-tested on the JVM. |
| `data` | package `com.ritoghosh.ttt.data` in `:app` | Room database, repositories, backup/restore, alarms. Converts rows ↔ `core` models in `Mappers.kt`. |
| `ui` | package `com.ritoghosh.ttt.ui` in `:app` | Compose screens and ViewModels. Talks to repositories only, never to the database. |

Because `:core` has no Android plugin, Android classes cannot be imported there; the compiler enforces the rule.

## Wiring

- `TttApplication` creates one `AppContainer` (lazily).
- `AppContainer` builds every long-lived object (database, repositories, clock, scheduler, backup services) by hand. No DI framework. It also owns one app-wide coroutine scope, used only for the auto-backup so it can finish after the screen is gone.
- `MainActivity` reads the container and shows `AppNav`.
- `AppNav` is the frame: hamburger drawer (Log, Settings, About), top bar, bottom tabs (Todo, Plan, Track), and a `NavHost` with string routes listed in `ui/nav/Destination.kt`.

## Folder map (current, M7)

```
core/src/main/kotlin/com/ritoghosh/ttt/core/
  Constants.kt               every named number from the spec
  model/                     Task, TaskDeletion, TagRules, Block, Entry, TimerState
  time/                      Clock (+ DeviceClock), TimeRange, DayRange
  plan/                      BlockRules (validation, overlap), BlockTimes (editor time arithmetic)
  timer/                     PomodoroMachine (+ PomodoroSteps), TimerEvent, TimerMath, PomodoroSettings
  stats/                     EntryBlockMatcher (plan vs actual), WorkedTime, TagTotals
  settings/Settings.kt       setting keys, ranges, parsing with safe fallbacks
  backup/BackupRules.kt      backup file names, which old backups to delete, when an auto-backup is due
  report/                    ReportBuilder (+ ReportContext, SummarySheet, PlanSheets, ActivitySheets),
                             ReportTable, ReportRange, ReportWriter, XlsxWriter (+ XlsxSheet, XlsxText)
core/src/test/kotlin/...     unit tests; testing/TestTime.kt has FakeClock and time helpers
app/src/main/java/com/ritoghosh/ttt/
  TttApplication.kt          owns the AppContainer; installs a pending restore before the database opens
  AppContainer.kt            all wiring
  MainActivity.kt            single activity
  data/db/                   AppDatabase, Entities, Daos, Mappers, Migrations
  data/repo/                 TaskRepository, BlockRepository, TimerRepository, EntryRepository, SettingsRepository
  data/alarm/                PhaseAlarmScheduler, PhaseAlarmReceiver, Notifications (TimerNotifications)
  data/backup/               BackupService (VACUUM INTO snapshot), RestoreService (+ installPendingRestore),
                             AutoBackup (folder backups when the app is left)
  data/report/               ReportService (load everything, build, write the .xlsx)
  ui/theme/                  Color, Type, Spacing, Theme, TagColors (all styling tokens)
  ui/nav/                    Destination, AppNav (frame), AppNavHost (routes + ViewModel factories),
                             AppBars, AppDrawer, TagFilterHolder, ScreenRequests
  ui/components/             EmptyState, TagChip, TagFilterRow, ErrorText, TaskPicker,
                             TimeField (+ DateField), TimeFormat, RestoreBackupLauncher (+ link)
  ui/todo/                   TodoScreen, TodoViewModel, TodoUiState, TaskRow,
                             TaskEditorSheet, TaskEditorViewModel, TaskEditorDeleteDialog
  ui/plan/                   PlanScreen, PlanViewModel, PlanUiState, DayTimeline,
                             BlockEditorSheet, BlockEditorViewModel, BlockEditorUiState
  ui/track/                  TrackScreen, TrackViewModel, TrackUiState, TimerFace (+ buttons),
                             TrackParts (note card, today row, hints), TrackDialogs
  ui/log/                    LogScreen, LogViewModel, LogUiState, LogDialogs
  ui/settings/               SettingsScreen, SettingsViewModel, BackupSection, ReportSection (+ range dialog)
  ui/about/AboutScreen.kt
app/schemas/                 Room's exported schema, one JSON per version (commit these)
```

The target layout is in `SPEC.md` 4.2; folders are added as milestones need them.

## Key paths

Filled in as they are built:

- **Add a task** (M2): the add field calls `TodoViewModel.onAddTask` → `TaskRepository.addTask` → `parseQuickAdd` and `validateTaskTitle` (core) → one transaction inserts the task row, any new tag rows and the task_tag links. Room's `Flow` re-emits the task list; `buildTodoUiState` filters and sorts it; the screen redraws.
- **Plan a block** (M3): "+" (or "Schedule a block" in the task editor, passed through `ScreenRequests`) opens `BlockEditorSheet` → `BlockEditorViewModel.onSave` turns date + times into instants (`BlockTimes`) → `BlockRepository.saveBlock` runs `BlockRules.checkBlock` against the same day's blocks and writes, in one transaction → the result (OK, rule broken, or the clashing block) comes back to the sheet. The day's blocks are a Room `Flow`, so the timeline redraws by itself.
- **Plan vs actual** (M5): the Timeblock screen reads the day's blocks and sessions; `workedInBlockMs` (core) gives "1h 35m worked" per block, and sessions are drawn as thin bars in a second column. `EntryBlockMatcher` classifies sessions (in block / continued / unplanned) for the report.
- **Settings** (M5): stored in the `setting` table; `core/settings` turns text into values with safe fallbacks. The timer reads them on every event (so a change applies from the next phase), and MainActivity applies the theme.
- **Back up** (M6): Settings → "Back up now" → the system "Save as" picker → `BackupService.writeBackupTo` makes a `VACUUM INTO` snapshot in the cache folder and copies it to the chosen document. **Auto-backup:** `MainActivity.onStop` → `AutoBackup.runIfDue` (on the container's app-wide scope) → if a folder is set and the last one is 24 h old, the same snapshot is written into the folder through `DocumentsContract`, then `backupsToDelete` (core) picks old files to remove.
- **Restore** (M6): "Restore backup" (Settings, or the link on the empty Todo screen) → `rememberRestoreBackupLauncher` checks the app is empty → file picker → `RestoreService.prepare` copies, checks, migrates and resets the file, leaving `pending-restore.db` → the app restarts itself → `TttApplication.onCreate` renames it over `ttt.db` before the container opens the database → MainActivity says "Backup restored". The live database is never replaced while open.
- **Global tag filter**: `TagFilterHolder` (activity-scoped ViewModel) holds the selected tag; each screen's ViewModel combines it with its data.
- **Run a pomodoro** (M4): a button in `TrackScreen` → `TrackViewModel` sends a `TimerEvent` → `TimerRepository.dispatch` reads `timer_state`, calls `transition()` in `PomodoroMachine` (pure, core), and in **one transaction** saves any finished session to `entry` and the new state (with the note-prompt id). After the transaction it applies the `AlarmAction` (`PhaseAlarmScheduler`) and redraws the ongoing notification (`TimerNotifications`, a system chronometer, no service). The Room `Flow` of `timer_state` updates the screen; a 1-second tick (only while visible) moves the clock.
- **Planned end** (M4): the alarm fires `PhaseAlarmReceiver` → `TimerRepository.onPlannedEndAlarm`: a break ends (Evaluate) and "Break over" is posted; work continues as overtime and "Planned time reached" is posted. On every app open `MainActivity.onStart` calls `recoverOnAppStart` (Evaluate + re-schedule a lost alarm).
- **Export a report** (M7): Settings → "Export report" → `ReportSection` asks for a range (All time / This month / Custom) → the system "Save as" picker → `SettingsViewModel.onReportDestinationChosen` → `ReportService` reads every task, block and session in one transaction → `buildReport` (core) makes six `ReportTable`s → `XlsxWriter` (core, `java.util.zip` only) writes them to the chosen document.
