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

## Folder map (current, M2)

```
core/src/main/kotlin/com/ritoghosh/ttt/core/
  Constants.kt               every named number from the spec
  model/                     Task, TaskDeletion, TagRules, Block, Entry, TimerState
  time/                      Clock (+ DeviceClock), TimeRange, DayRange
  plan/BlockRules.kt         block validation and overlap
core/src/test/kotlin/...     unit tests; testing/TestTime.kt has FakeClock and time helpers
app/src/main/java/com/ritoghosh/ttt/
  TttApplication.kt          owns the AppContainer
  AppContainer.kt            all wiring
  MainActivity.kt            single activity
  data/db/                   AppDatabase, Entities, Daos, Mappers, Migrations
  data/repo/TaskRepository.kt
  ui/theme/                  Color, Type, Spacing, Theme, TagColors (all styling tokens)
  ui/nav/                    Destination, AppNav, AppBars, AppDrawer, TagFilterHolder
  ui/components/             EmptyState, TagChip, TagFilterRow, ErrorText
  ui/todo/                   TodoScreen, TodoViewModel, TodoUiState, TaskRow,
                             TaskEditorSheet, TaskEditorViewModel, TaskEditorDeleteDialog
  ui/about/AboutScreen.kt
app/schemas/                 Room's exported schema, one JSON per version (commit these)
```

The target layout is in `SPEC.md` 4.2; folders are added as milestones need them.

## Key paths

Filled in as they are built:

- **Add a task** (M2): the add field calls `TodoViewModel.onAddTask` → `TaskRepository.addTask` → `parseQuickAdd` and `validateTaskTitle` (core) → one transaction inserts the task row, any new tag rows and the task_tag links. Room's `Flow` re-emits the task list; `buildTodoUiState` filters and sorts it; the screen redraws.
- **Global tag filter**: `TagFilterHolder` (activity-scoped ViewModel) holds the selected tag; each screen's ViewModel combines it with its data.
- **Run a pomodoro** (M4): TrackScreen → TrackViewModel → TimerRepository → `PomodoroMachine` (core) → one DB transaction; alarm and notification updated after.
- **Export a report** (M7): Settings → `ReportBuilder` (core) → `XlsxWriter` (core) → system save dialog.
