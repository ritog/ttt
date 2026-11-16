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

Written in M7.

## Add a screen to the menu

Don't, unless `SPEC.md` is changed first. Mechanically: add an entry to `ui/nav/Destination.kt`, a string, and a `composable(...)` line in `AppNav.kt`.

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
- [ ] Report opens in LibreOffice and Google Sheets (M7).
