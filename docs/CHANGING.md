# Changing the app

Recipes for common changes, and the manual test checklist. Keep recipes short and exact.

## Change the colour palette

1. Edit the tokens in `app/src/main/java/com/ritoghosh/ttt/ui/theme/Color.kt` (light and dark).
2. If you change a background, also update `res/values/colors.xml` and `res/values-night/colors.xml` (`window_background`), so the first frame matches.
3. Check text contrast stays WCAG AA (4.5:1 for body text).

## Switch the font to Inter

1. Download Inter (variable) from the official Inter project; copy the `.ttf` to `app/src/main/res/font/inter.ttf` (lowercase name).
2. In `ui/theme/Type.kt`, set `AppFontFamily = FontFamily(Font(R.font.inter))`.
3. Record the change in `docs/DECISIONS.md`.

## Change a pomodoro default

1. Edit `DEFAULT_WORK_MINUTES`, `DEFAULT_SHORT_BREAK_MINUTES`, `DEFAULT_LONG_BREAK_MINUTES` or `DEFAULT_WORK_PHASES_BEFORE_LONG_BREAK` in `core/src/main/kotlin/com/ritoghosh/ttt/core/Constants.kt`.
2. Update `ConstantsTest` if it pins the value, and SPEC 9.5.
3. From M5 on, users' own settings override these defaults; changing a default affects only people who never changed that setting.

## Add a column (migration steps)

Example: add `estimate_minutes` to `task`.

1. Add the field to `TaskEntity` in `data/db/Entities.kt`, with a default (`@ColumnInfo(name = "estimate_minutes", defaultValue = "0") val estimateMinutes: Int = 0`).
2. Bump `DATABASE_VERSION` in `data/db/Migrations.kt` (1 → 2).
3. In the same file add the migration and list it in `ALL_MIGRATIONS`:
   ```kotlin
   val MIGRATION_1_2 = object : Migration(1, 2) {
       override fun migrate(db: SupportSQLiteDatabase) {
           db.execSQL("ALTER TABLE task ADD COLUMN estimate_minutes INTEGER NOT NULL DEFAULT 0")
       }
   }
   val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
   ```
4. Build once; Room writes `app/schemas/.../2.json`. Commit it next to `1.json` (never edit old JSON files).
5. Add a test to `MigrationTest`: create version 1 with `helper.createDatabase(name, 1)`, insert a row with SQL, close, then `helper.runMigrationsAndValidate(name, 2, true, MIGRATION_1_2)` and check the row.
6. Update the mappers, `docs/DATA.md`, and `docs/DECISIONS.md`.

Never use `fallbackToDestructiveMigration`: it deletes the user's data.

## Add a sheet to the report

1. Write a function in `core/report/` that returns a `ReportTable`: a title (at most 31 characters), a header and rows of `Cell`s (`Text`, `Number`, `Date`, `Time`, `DateTime`, `Empty`). Use the helpers in `ReportContext.kt` (`minutesCell`, `hoursCell`, `dateCell`, …) so rounding stays at the edge, and read records from `ReportContext` (`entriesInRange`, `blocksInRange`, `matches`, …) rather than filtering again.
2. Add it to the list in `buildReport` (`ReportBuilder.kt`); sheet order in the file is list order.
3. Add a test in `ReportBuilderTest` with rows written out from the fixture in `ReportFixture.kt`.
4. Describe the sheet in `DATA.md` ("Report") and note the change in `DECISIONS.md`.

The writer needs no change: any `ReportTable` becomes a sheet. To add a column, add it to the header list and to the row function in the same position.

## Add a screen to the menu

Don't, unless `SPEC.md` is changed first. Mechanically: add an entry to `ui/nav/Destination.kt`, a string, and a `composable(...)` line in `AppNav.kt`.

## Fill the app with test data

`tools/make_test_db.py` fills an **empty** database with made-up data: `--demo` for a small realistic
set (README screenshots), or `--tasks 5000 --sessions 20000` (the default) for the performance check.
Use an emulator or a test phone, never your real one: it starts with clearing the app's data.

```sh
export ANDROID_SERIAL=emulator-5554          # the emulator, from `adb devices`
adb shell pm clear com.ritoghosh.ttt         # deletes the app's data
adb shell am start -W -n com.ritoghosh.ttt/.MainActivity && sleep 3   # Room creates the tables
adb shell am force-stop com.ritoghosh.ttt
mkdir -p /tmp/ttt && for f in ttt.db ttt.db-wal ttt.db-shm; do
  adb exec-out run-as com.ritoghosh.ttt cat databases/$f > /tmp/ttt/$f; done
python3 tools/make_test_db.py /tmp/ttt/ttt.db --demo
adb exec-out run-as com.ritoghosh.ttt rm -f databases/ttt.db-wal databases/ttt.db-shm
cat /tmp/ttt/ttt.db | adb shell run-as com.ritoghosh.ttt sh -c 'cat > databases/ttt.db'
```

`run-as` works only with the debug build. To measure a release build, load the data with the debug
build first, then install the release APK over it (same signing key needed, see README).

## Performance sanity check (SPEC 18, M8)

1. Load `--tasks 5000 --sessions 20000` as above, then use a release build (debug builds are several times slower).
2. Cold start: run `adb shell am start -W -n com.ritoghosh.ttt/.MainActivity` a few times after
   `am force-stop`, and read `adb logcat | grep "Fully drawn"`: Todo reports itself drawn once its list has loaded.
3. Screens: switch tabs and Log ranges; they should feel instant. `adb shell screenrecord` plus
   `ffmpeg` frame times give numbers if something feels slow.
4. Report: Settings → Export report → All time; time from Save to "Report saved.".

Results from M8 (emulator, Medium Phone API 37, release build) are in `DECISIONS.md`.

## Manual test checklist

Tick before each release (full list grows with each milestone).

- [ ] App starts on Todo; the three tabs switch; the hamburger opens Log, Settings, About; back returns to the tab.
- [ ] Todo: add `Write outline #book #writing` → title and two tag chips; Enter keeps the keyboard open.
- [ ] Todo: check/uncheck moves a task between open and Done; Done is collapsed by default.
- [ ] Todo: tag filter shows only matching tasks; Clear removes it; the filter survives rotation and resets after the app is killed.
- [ ] Editor: rename, add/remove tags, note; Save persists after an app restart; Cancel discards.
- [ ] Editor: delete asks for confirmation; the last task with a tag removes that tag from the filter row.
- [ ] About: the GitHub link opens github.com/ritog in the browser.
- [ ] Timeblock: "+" opens the editor; a 60-minute block appears on the timeline, tinted by its task's first tag.
- [ ] Timeblock: an overlapping block is refused with a message naming the other block; a 10-minute block is refused.
- [ ] Timeblock: a block from 23:00 to 00:00 saves; 23:30 to 00:30 is refused ("must end by midnight").
- [ ] Timeblock: tapping a block opens it for editing; Delete removes it; changes survive an app restart.
- [ ] Timeblock: previous/next day and Today work; the current-time line shows only on today.
- [ ] Timeblock: the tag filter hides blocks of other tasks.
- [ ] Task editor: "Schedule a block" opens the Timeblock editor with that task chosen; the Todo row then shows "Next block …".
- [ ] Light and dark themes look right (switch the phone's dark mode).
- [ ] 200% font scale: nothing clipped.
- [ ] TalkBack: hamburger and back buttons are announced.
- [ ] Track: pick a task, Start; the countdown runs; Pause/Resume; the ongoing notification counts down.
- [ ] Track: at the planned end a "Planned time reached" alert arrives; the screen and notification count up "+mm:ss"; Finish queues a break that waits for a tap.
- [ ] Track: a break ends by itself (alert "Break over"); the next work phase waits for a tap; Skip break works.
- [ ] Track: Stop after ≥ 1 minute asks first; under a minute nothing is saved.
- [ ] Track: after more than an hour of overtime, Finish/Stop asks "Keep all … / Keep the planned … only".
- [ ] Track: the note card appears after a session; Save stores the note; Skip closes it.
- [ ] Timer survives: kill the app (swipe away) mid-work → reopen → same time; lock the phone for a few minutes → correct; reboot mid-session → reopen → correct time, alarm re-scheduled.
- [ ] First Start asks for notification permission (Android 13+); with it denied the timer still works.
- [ ] Android 14+: with exact alarms not allowed, Track shows the hint; "Allow exact alarms" opens the system page.
- [ ] Tapping a timer notification opens the Track tab.
- [ ] "Start timer" from the task editor and from a block opens Track with that task selected.
- [ ] Timeblock: sessions appear as thin bars on the right; a block shows "· 1h 35m worked" when work fell inside it.
- [ ] Log: Today / This week / This month; time per tag; sessions grouped by day with totals; a session from 23:50 to 00:15 counts on the first day.
- [ ] Log: tap a session → edit the note → Save; Delete asks to confirm; the tag filter narrows sessions and totals.
- [ ] Settings: − / + change pomodoro lengths (stop at their limits); a change applies to the next phase, not the running one.
- [ ] Settings: theme Light / Dark / Same as the phone switches at once, status bar icons included.
- [ ] Todo: a task with sessions shows "… worked" under its title.
- [ ] Settings → Back up now → save to Downloads: "Backup saved."; the file is named `ttt-backup-YYYY-MM-DD-HHmm.db` and opens with `sqlite3` (see DATA.md).
- [ ] Restore with data present: "Restore works on an empty app…" appears before any file picker.
- [ ] Restore round trip: back up, clear the app's storage (Android settings → Apps → ttt → Storage), open the app → Todo shows "Restore a backup" → pick the file → the app restarts with "Backup restored." and all tasks, blocks, sessions and settings are back; the timer is idle; auto-backup is off.
- [ ] Restore refuses a random file ("not a ttt backup") and leaves the app empty and working.
- [ ] Auto-backup: choose a folder; leave the app (Home) → a backup appears in the folder; leave again at once → no second file (24 h rule); "Last auto-backup" shows the time.
- [ ] Auto-backup pruning: set Keep last to 2, put three older `ttt-backup-…` files in the folder (copies with earlier dates in the name) plus an unrelated file → after the next auto-backup only the newest two backups remain and the unrelated file is untouched.
- [ ] Stop auto-backup: the folder line says Off; the app no longer appears under the folder's access list in the Files app.
- [ ] Settings → Export report → All time → save: "Report saved."; the file is named `ttt-report-YYYY-MM-DD.xlsx`.
- [ ] The report opens in LibreOffice Calc and in Google Sheets (upload to Drive, open with Sheets): six sheets in the order Summary, Tasks, Blocks, Sessions, Daily, Tags.
- [ ] In both: dates show as `yyyy-mm-dd` and times as `hh:mm` and sort as dates; minutes are numbers that SUM correctly; the header row is bold and stays put when scrolling; the filter arrows work.
- [ ] Summary states the range and the generation time; "This month" and a Custom range give smaller numbers than All time; a Custom range with the end before the start cannot be exported.
- [ ] A task note with `<`, `&`, quotes, an emoji and a line break shows unchanged in the Tasks sheet.
- [ ] Launcher and splash show the teal three-bar icon; with themed icons on (Android 13+), it follows the wallpaper colours.
- [ ] Empty states: a new install shows one sentence on Todo, Timeblock (a day without blocks) and Log (a range without sessions).
- [ ] 200% font scale: Timeblock hour labels stay on one line ("16:00").
- [ ] TalkBack on Todo: a task's checkbox is read as "Done: <title>, checkbox".
- [ ] Release APK (signed, see README) installs, starts, and does all of the above on a real phone.
