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

### block — planned time for a task (used from M3)

| column | type | meaning |
|---|---|---|
| id | INTEGER, primary key | |
| task_id | INTEGER, not null | → task.id; deleting the task deletes its blocks |
| start_at | INTEGER, not null | epoch ms, on a 5-minute mark of local time |
| end_at | INTEGER, not null | epoch ms, after start_at, at most the next local midnight |

Indexes: start_at, task_id. Blocks never overlap (checked in code, `BlockRules`).

### entry — one recorded work session (used from M4)

| column | type | meaning |
|---|---|---|
| id | INTEGER, primary key | |
| task_id | INTEGER, not null | → task.id; **a task with entries cannot be deleted** (RESTRICT) |
| started_at | INTEGER, not null | wall-clock start (epoch ms) |
| ended_at | INTEGER, not null | wall-clock end (epoch ms) |
| paused_ms | INTEGER, not null | total pause time inside the session |
| note | TEXT, not null, default '' | |

Worked time = `ended_at − started_at − paused_ms`. Indexes: started_at, task_id.

### timer_state — the pomodoro timer (one row, id = 1; used from M4)

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

If the row is missing, the timer is idle.

### setting — app settings (used from M5)

`key` TEXT primary key, `value` TEXT. Keys are listed in SPEC 9.5. Settings live in the database so backups include them.

### meta — facts about this database file

`key` TEXT primary key, `value` TEXT. Written once when the file is created: `created_at` (epoch ms) and `app_version_at_creation`.

## Worked-time simplification

To be written in M5 (`EntryBlockMatcher`): pause time inside a session is spread evenly over its wall-clock window when computing time worked inside a block.

## Backup format

To be written in M6. Summary: a full SQLite snapshot made with `VACUUM INTO`, named `ttt-backup-YYYY-MM-DD-HHmm.db`.

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
