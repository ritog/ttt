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
- `AppContainer` builds every long-lived object (database, repositories, clock, scheduler) by hand. No DI framework.
- `MainActivity` reads the container and shows `AppNav`.
- `AppNav` is the frame: hamburger drawer (Log, Settings, About), top bar, bottom tabs (Todo, Plan, Track), and a `NavHost` with string routes listed in `ui/nav/Destination.kt`.

## Folder map (current, M4)

```
core/src/main/kotlin/com/ritoghosh/ttt/core/
  Constants.kt               every named number from the spec
  model/                     Task, TaskDeletion, TagRules, Block, Entry, TimerState
  time/                      Clock (+ DeviceClock), TimeRange, DayRange
  plan/                      BlockRules (validation, overlap), BlockTimes (editor time arithmetic)
  timer/                     PomodoroMachine (+ PomodoroSteps), TimerEvent, TimerMath, PomodoroSettings
core/src/test/kotlin/...     unit tests; testing/TestTime.kt has FakeClock and time helpers
app/src/main/java/com/ritoghosh/ttt/
  TttApplication.kt          owns the AppContainer
  AppContainer.kt            all wiring
  MainActivity.kt            single activity
  data/db/                   AppDatabase, Entities, Daos, Mappers, Migrations
  data/repo/                 TaskRepository, BlockRepository, TimerRepository, EntryRepository
  data/alarm/                PhaseAlarmScheduler, PhaseAlarmReceiver, Notifications (TimerNotifications)
  ui/theme/                  Color, Type, Spacing, Theme, TagColors (all styling tokens)
  ui/nav/                    Destination, AppNav (frame), AppNavHost (routes + ViewModel factories),
                             AppBars, AppDrawer, TagFilterHolder, ScreenRequests
  ui/components/             EmptyState, TagChip, TagFilterRow, ErrorText, TaskPicker,
                             TimeField (+ DateField), TimeFormat
  ui/todo/                   TodoScreen, TodoViewModel, TodoUiState, TaskRow,
                             TaskEditorSheet, TaskEditorViewModel, TaskEditorDeleteDialog
  ui/plan/                   PlanScreen, PlanViewModel, PlanUiState, DayTimeline,
                             BlockEditorSheet, BlockEditorViewModel, BlockEditorUiState
  ui/track/                  TrackScreen, TrackViewModel, TrackUiState, TimerFace (+ buttons),
                             TrackParts (note card, today row, hints), TrackDialogs
  ui/about/AboutScreen.kt
app/schemas/                 Room's exported schema, one JSON per version (commit these)
```

The target layout is in `SPEC.md` 4.2; folders are added as milestones need them.

## Key paths

Filled in as they are built:

- **Add a task** (M2): the add field calls `TodoViewModel.onAddTask` → `TaskRepository.addTask` → `parseQuickAdd` and `validateTaskTitle` (core) → one transaction inserts the task row, any new tag rows and the task_tag links. Room's `Flow` re-emits the task list; `buildTodoUiState` filters and sorts it; the screen redraws.
- **Plan a block** (M3): "+" (or "Schedule a block" in the task editor, passed through `ScreenRequests`) opens `BlockEditorSheet` → `BlockEditorViewModel.onSave` turns date + times into instants (`BlockTimes`) → `BlockRepository.saveBlock` runs `BlockRules.checkBlock` against the same day's blocks and writes, in one transaction → the result (OK, rule broken, or the clashing block) comes back to the sheet. The day's blocks are a Room `Flow`, so the timeline redraws by itself.
- **Global tag filter**: `TagFilterHolder` (activity-scoped ViewModel) holds the selected tag; each screen's ViewModel combines it with its data.
- **Run a pomodoro** (M4): a button in `TrackScreen` → `TrackViewModel` sends a `TimerEvent` → `TimerRepository.dispatch` reads `timer_state`, calls `transition()` in `PomodoroMachine` (pure, core), and in **one transaction** saves any finished session to `entry` and the new state (with the note-prompt id). After the transaction it applies the `AlarmAction` (`PhaseAlarmScheduler`) and redraws the ongoing notification (`TimerNotifications`, a system chronometer, no service). The Room `Flow` of `timer_state` updates the screen; a 1-second tick (only while visible) moves the clock.
- **Planned end** (M4): the alarm fires `PhaseAlarmReceiver` → `TimerRepository.onPlannedEndAlarm`: a break ends (Evaluate) and "Break over" is posted; work continues as overtime and "Planned time reached" is posted. On every app open `MainActivity.onStart` calls `recoverOnAppStart` (Evaluate + re-schedule a lost alarm).
- **Export a report** (M7): Settings → `ReportBuilder` (core) → `XlsxWriter` (core) → system save dialog.
