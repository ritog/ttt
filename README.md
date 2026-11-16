# ttt

Time blocking, time tracking and to-do, in one small Android app.

- **To-do**: what I want to do.
- **Plan**: when I will do it (time blocks on a day timeline).
- **Track**: a pomodoro timer that records when I actually did it, with notes.

Free and open source. No accounts, no ads, no analytics, no network access.

> Status: milestone **M4**. Todo, Timeblock and Track work end to end: tasks and tags, time blocks on a day timeline,
> and a pomodoro timer that records sessions with notes. Log and Settings are still placeholders.
> See `SPEC.md` section 18 for the build order.

## Screenshots

Added in M8.

## Build and install (Linux)

Requirements: Android Studio (or the Android SDK command-line tools) and a JDK 17 or newer.

```sh
cd ~/AndroidStudioProjects/ttt
./gradlew :core:test            # pure-Kotlin rules, no device needed
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest   # database tests, on a connected phone or emulator
./gradlew :app:assembleDebug    # APK in app/build/outputs/apk/debug/
./gradlew :app:installDebug     # install on a connected phone (USB debugging on)
```

## Documentation

- `SPEC.md` — what the app does and does not do (the source of truth).
- `docs/ARCHITECTURE.md` — layers, folders, data flow.
- `docs/DATA.md` — database tables and the backup format.
- `docs/DEPENDENCIES.md` — every dependency and why.
- `docs/CHANGING.md` — recipes for common changes, and the manual test checklist.
- `docs/DECISIONS.md` — dated log of decisions.

## Licence

To be chosen before the first release (MIT or GPL-3.0); see `docs/DECISIONS.md`.
