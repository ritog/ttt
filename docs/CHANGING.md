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

Written in M4/M5.

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
- [ ] Light and dark themes look right (switch the phone's dark mode).
- [ ] 200% font scale: nothing clipped.
- [ ] TalkBack: hamburger and back buttons are announced.
- [ ] Timer survives app kill, phone sleep and reboot (M4).
- [ ] Report opens in LibreOffice and Google Sheets (M7).
