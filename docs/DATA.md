# Data

Plain-language description of everything the app stores. **The schema is a contract**: changing it needs a Room migration, a migration test, and an update to this file (recipe in `CHANGING.md`).

Database file: `ttt.db` in the app's private storage. Schema version: **1** (kept in SQLite's `user_version`). The exact schema Room generates is committed in `app/schemas/com.ritoghosh.ttt.data.db.AppDatabase/1.json`.

All tables exist from version 1, including those the app starts using in later milestones.

## Time rules

- Every instant is stored as **epoch milliseconds, UTC** (an `INTEGER`).
- "Today", "this week" etc. are worked out with the phone's **current** time zone when shown or reported. Weeks start on Monday.
- A session or block belongs to the **local day it starts on**. A session from 23:50 to 00:15 counts fully toward the first day.
- Known and accepted: after changing time zones, old records keep their instants but may land on a different local day. This is not corrected.

## Tables

### task — things to do

| column | type | meaning |
|---|---|---|
| id | INTEGER, primary key | assigned by the database |
| title | TEXT, not null | trimmed, 1–200 characters, without `#tags` |
| is_done | INTEGER, not null | 0 = open, 1 = done |
| created_at | INTEGER, not null | when the task was added (epoch ms) |
| done_at | INTEGER, nullable | when it was marked done; empty while open |
| note | TEXT, not null, default '' | free text |

### tag — tag names in use

| column | type | meaning |
|---|---|---|
| id | INTEGER, primary key | |
| name | TEXT, not null, unique | lowercase, 1–30 of `a-z 0-9 - _` |

A tag row exists only while at least one task uses it; the app deletes unused tags whenever a task's tags change or a task is deleted.

### task_tag — which task has which tag

| column | type | meaning |
|---|---|---|
| task_id | INTEGER | → task.id; deleting the task deletes these rows |
| tag_id | INTEGER | → tag.id; deleting the tag deletes these rows |

Primary key: (task_id, tag_id). Index on tag_id.

### block — planned time for a task

| column | type | meaning |
|---|---|---|
| id | INTEGER, primary key | |
| task_id | INTEGER, not null | → task.id; deleting the task deletes its blocks |
| start_at | INTEGER, not null | epoch ms, on a 5-minute mark of local time |
| end_at | INTEGER, not null | epoch ms, after start_at, at most the next local midnight |

Indexes: start_at, task_id. Blocks never overlap (checked in code, `BlockRules`).

### entry — one recorded work session

| column | type | meaning |
|---|---|---|
| id | INTEGER, primary key | |
| task_id | INTEGER, not null | → task.id; **a task with entries cannot be deleted** (RESTRICT) |
| started_at | INTEGER, not null | wall-clock start (epoch ms) |
| ended_at | INTEGER, not null | wall-clock end (epoch ms) |
| paused_ms | INTEGER, not null | total pause time inside the session |
| note | TEXT, not null, default '' | |

Worked time = `ended_at − started_at − paused_ms`. Indexes: started_at, task_id.

### timer_state — the pomodoro timer (one row, id = 1)

| column | meaning |
|---|---|
| phase | `NONE`, `WORK`, `SHORT_BREAK` or `LONG_BREAK` (text) |
| status | `IDLE`, `RUNNING`, `PAUSED` or `AWAITING_START` (text) |
| task_id | task being worked on, or empty |
| phase_length_ms | planned length of the current phase |
| accumulated_ms | time counted in this phase before the current run |
| running_since_ms | when the current run began; only while running |
| paused_since_ms | when the current pause began; only while paused |
| session_started_at_ms | start of the current work phase |
| planned_end_reached_at_ms | when the work phase reached its planned length |
| completed_work_phases | work phases finished in the current run of pomodoros |
| note_prompt_entry_id | session waiting for its note card, or empty |

If the row is missing, the timer is idle. If phase or status hold text the app does not know (a damaged or hand-edited file), the app also treats the timer as idle rather than failing to start. Time is never stored as a running counter: elapsed time is always worked out from these timestamps, which is why the timer stays right after the app is killed or the phone sleeps.

### setting — app settings

`key` TEXT primary key, `value` TEXT. Settings live in the database so backups include them. A missing key means "use the default"; a value out of range or unreadable is also treated as the default.

| key | meaning | default | allowed |
|---|---|---|---|
| work_minutes | length of a work phase | 25 | 1–180 |
| short_break_minutes | short break | 5 | 1–60 |
| long_break_minutes | long break | 15 | 1–120 |
| work_phases_before_long_break | long break after every Nth work phase | 4 | 1–12 |
| theme | `system`, `light` or `dark` | system | |
| auto_backup_folder_uri | folder chosen for auto-backups (a document-tree URI); absent = off | (absent) | |
| auto_backup_keep | how many auto-backups to keep in that folder | 7 | 1–30 |
| last_auto_backup_at | when the last auto-backup was written, epoch ms | (absent) | |

### meta — facts about this database file

`key` TEXT primary key, `value` TEXT. Written once when the file is created: `created_at` (epoch ms) and `app_version_at_creation`.

## Plan vs actual (how sessions are matched to blocks)

`core/stats/EntryBlockMatcher.kt`, for each session and the blocks of the **same task**:

1. **In block** — the session overlaps a block (with several, the largest overlap wins).
2. **Continued after block** — it overlaps none, but starts at most 30 minutes after a block's end, or after the end of a session already counted as continued for that block (a chain).
3. **Unplanned** — everything else.

**Worked time inside a block** (`WorkedTime.kt`) = for each session of the block's task, `overlap(session window, block window) × (worked ÷ wall-clock length)`. This spreads a session's pauses evenly over its window: a simplification, since the real pause may have happened outside the block. All sums are in milliseconds; minutes appear only on screen and in the report.

**Tag totals** count a session fully under every tag of its task, so totals can add up to more than the time worked.

## Backup format

A backup is the whole database in one ordinary SQLite 3 file. Nothing is converted or compressed, so any SQLite tool on any platform can open it.

- **Name:** `ttt-backup-YYYY-MM-DD-HHmm.db`, local time when it was made (for example `ttt-backup-2026-11-16-1830.db`). Auto-backup only ever deletes files that match this pattern exactly.
- **How it is made:** `VACUUM INTO` on the live database. SQLite writes a complete, consistent copy as it is at that moment, while the app keeps running. The single `.db` file is the whole backup; no `-wal` or `-shm` files go with it.
- **Contents:** every table above (`task`, `tag`, `task_tag`, `block`, `entry`, `timer_state`, `setting`, `meta`) plus Room's `room_master_table` (a hash Room uses to check the schema).
- **Schema version:** `PRAGMA user_version` (1 today). A file with a higher number came from a newer app and is refused; a lower number is upgraded by the normal migrations when restored.
- **Restore checks, in order:** the app has no tasks, blocks or sessions → the file can be read → it opens as SQLite → `PRAGMA integrity_check` says `ok` → it has a `task` table and a version of at least 1 → the version is not newer → Room opens it (running migrations and checking the schema).
- **What restore resets:** `timer_state` is emptied (reads as idle), and `auto_backup_folder_uri` and `last_auto_backup_at` are removed, because a running timer and a folder permission belong to the old phone. Everything else, including the other settings and `meta`, is kept.
- **How restore is installed:** the checked copy waits in the app's databases folder as `pending-restore.db`; the app restarts and, before anything opens the database, renames it over `ttt.db`. If the rename fails, the current database stays and the user still has the original backup file.

## Report (export)

Settings → Export report writes one `.xlsx` file (`ttt-report-YYYY-MM-DD.xlsx`) with six sheets: Summary, Tasks, Blocks, Sessions, Daily, Tags. It is read-only and one-way; nothing imports it back. No ids appear anywhere.

- **Range:** All time, This month, or Custom (whole local days, both ends included). A session or block is in the range if it **starts** in it, the same rule the Log uses.
- **What uses the range:** tracked and planned time, sessions, top tags and tasks (Summary); counts and minutes (Tasks); the rows of Blocks, Sessions and Daily; minutes worked (Tags).
- **What does not:** task counts, "upcoming" planned time (blocks starting after the export time), this week vs last week (Summary); created, done, first/last worked and next block (Tasks); the last-7 and last-30-day columns (Tags, counted back from the export time).
- **Plan status** (Sessions) and "continued after end" (Blocks) are worked out over all records, so the range's edges never change how a session is classified. See "Plan vs actual" above.
- **Rounding:** all sums are done in milliseconds. Minutes are whole and rounded down, as on the app's screens; hours have one decimal, rounded half up. A "Difference" column is the difference of the two minute cells in the same row.
- **Cells:** dates, times and date-times are real spreadsheet values (`yyyy-mm-dd`, `hh:mm`), minutes are plain numbers, tags are comma-separated text. Times are shown in the phone's time zone at export time. Text cells are cut at 32,000 characters.

## Inspecting a backup on Linux

```sh
sqlite3 ttt-backup-2026-11-16-1830.db
.tables
.schema task
SELECT title, datetime(created_at / 1000, 'unixepoch', 'localtime') FROM task LIMIT 10;
SELECT task.title, group_concat(tag.name)
  FROM task LEFT JOIN task_tag ON task_tag.task_id = task.id
  LEFT JOIN tag ON tag.id = task_tag.tag_id GROUP BY task.id;
```
