#!/usr/bin/env python3
"""
make_test_db.py — fills an empty ttt database with made-up tasks, tags, blocks and sessions.

Used for the M8 performance sanity check (SPEC 18: about 5,000 tasks and 20,000 sessions) and
for README screenshots (a small, realistic data set). See docs/CHANGING.md, "Fill the app with
test data".

Depends on: Python 3 standard library only.
Must not: be pointed at a real database. It only inserts rows, but the data is fake and mixed
in with whatever is there. It refuses a database that already has tasks.

Usage:
    python3 tools/make_test_db.py ttt.db --tasks 5000 --sessions 20000
    python3 tools/make_test_db.py ttt.db --demo
"""
import argparse
import datetime as dt
import random
import sqlite3
import sys

TAGS = ["book", "writing", "work", "admin", "health", "study", "home", "code"]

DEMO_TASKS = [
    ("Write chapter 3 outline", ["book", "writing"], "Start from the index cards."),
    ("Review pull requests", ["work", "code"], ""),
    ("Tax paperwork", ["admin"], "Receipts are in the blue folder."),
    ("Read two papers on spaced repetition", ["study"], ""),
    ("Fix the bike's back brake", ["home"], ""),
    ("Morning run plan", ["health"], ""),
    ("Draft talk abstract", ["writing", "work"], ""),
    ("Learn Room migrations", ["study", "code"], ""),
]

SESSION_MINUTES = 25
GAP_MINUTES = 5
DAY_START_HOUR = 8


def epoch_ms(moment):
    """Local datetime → epoch milliseconds, as the app stores times (DATA.md)."""
    return int(moment.timestamp() * 1000)


def insert_tags(db, names):
    ids = {}
    for name in names:
        ids[name] = db.execute("INSERT INTO tag (name) VALUES (?)", (name,)).lastrowid
    return ids


def insert_task(db, title, tags, note, created, done_at, tag_ids):
    task_id = db.execute(
        "INSERT INTO task (title, is_done, created_at, done_at, note) VALUES (?, ?, ?, ?, ?)",
        (title, 1 if done_at else 0, epoch_ms(created), epoch_ms(done_at) if done_at else None, note),
    ).lastrowid
    for tag in tags:
        db.execute("INSERT INTO task_tag (task_id, tag_id) VALUES (?, ?)", (task_id, tag_ids[tag]))
    return task_id


def insert_day(db, day, task_ids, sessions_per_day, add_blocks):
    """Back-to-back pomodoros from 08:00, and two blocks; sessions inside a block are its task's."""
    start = dt.datetime.combine(day, dt.time(DAY_START_HOUR))
    blocks = []
    if add_blocks:
        for offset_hours, length_minutes in ((1, 120), (6, 90)):
            block_start = start + dt.timedelta(hours=offset_hours)
            block_end = block_start + dt.timedelta(minutes=length_minutes)
            task_id = random.choice(task_ids)
            blocks.append((block_start, block_end, task_id))
            db.execute(
                "INSERT INTO block (task_id, start_at, end_at) VALUES (?, ?, ?)",
                (task_id, epoch_ms(block_start), epoch_ms(block_end)),
            )
    now = dt.datetime.now()
    for index in range(sessions_per_day):
        session_start = start + dt.timedelta(minutes=index * (SESSION_MINUTES + GAP_MINUTES))
        session_end = session_start + dt.timedelta(minutes=SESSION_MINUTES)
        if session_end > now:
            return
        in_block = [task for block_start, block_end, task in blocks if block_start <= session_start < block_end]
        task_id = in_block[0] if in_block else random.choice(task_ids)
        db.execute(
            "INSERT INTO entry (task_id, started_at, ended_at, paused_ms, note) VALUES (?, ?, ?, ?, ?)",
            (task_id, epoch_ms(session_start), epoch_ms(session_end), 0,
             "Good focus." if index % 7 == 0 else ""),
        )


def fill_bulk(db, task_count, session_count):
    tag_ids = insert_tags(db, TAGS)
    sessions_per_day = 14
    days = max(1, session_count // sessions_per_day)
    today = dt.date.today()
    first_day = today - dt.timedelta(days=days)
    task_ids = []
    for number in range(task_count):
        created = dt.datetime.combine(first_day, dt.time(7)) + dt.timedelta(days=number % days)
        done_at = created + dt.timedelta(days=3) if number % 3 == 0 else None
        if done_at and done_at > dt.datetime.now():
            done_at = None
        tags = random.sample(TAGS, k=number % 3)
        task_ids.append(insert_task(db, f"Task number {number + 1}", tags, "", created, done_at, tag_ids))
    for day_number in range(days + 1):
        insert_day(db, first_day + dt.timedelta(days=day_number), task_ids, sessions_per_day, True)
    for day_number in range(1, 15):  # upcoming blocks
        insert_day(db, today + dt.timedelta(days=day_number), task_ids, 0, True)


def fill_demo(db):
    tag_ids = insert_tags(db, TAGS)
    today = dt.date.today()
    created = dt.datetime.combine(today - dt.timedelta(days=20), dt.time(9))
    task_ids = [insert_task(db, t, tags, note, created, None, tag_ids) for t, tags, note in DEMO_TASKS]
    insert_task(db, "Renew passport", ["admin"], "", created, created + dt.timedelta(days=2), tag_ids)
    for day_number in range(14, 0, -1):
        insert_day(db, today - dt.timedelta(days=day_number), task_ids[:5], 6, True)
    insert_day(db, today, task_ids[:3], 4, True)
    insert_day(db, today + dt.timedelta(days=1), task_ids, 0, True)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("database", help="an empty ttt.db created by the app (Room makes the tables)")
    parser.add_argument("--tasks", type=int, default=5000)
    parser.add_argument("--sessions", type=int, default=20000)
    parser.add_argument("--demo", action="store_true", help="a small realistic set for screenshots")
    args = parser.parse_args()

    random.seed(42)  # the same data every run, so measurements compare
    db = sqlite3.connect(args.database)
    if db.execute("SELECT COUNT(*) FROM task").fetchone()[0] > 0:
        sys.exit("This database already has tasks; use an empty one.")
    with db:
        if args.demo:
            fill_demo(db)
        else:
            fill_bulk(db, args.tasks, args.sessions)
    db.execute("PRAGMA wal_checkpoint(TRUNCATE)")
    counts = {t: db.execute(f"SELECT COUNT(*) FROM {t}").fetchone()[0] for t in ("task", "block", "entry")}
    print(counts)
    db.close()


if __name__ == "__main__":
    main()
