# Dependencies

Every dependency, with one line on why. Versions live in `gradle/libs.versions.toml`. Add nothing without a line here (SPEC 3).

## Build plugins

| Plugin | Why |
|---|---|
| `com.android.application` (AGP 9.5) | Builds the Android app. AGP 9 compiles Kotlin itself, so there is no separate kotlin-android plugin. |
| `org.jetbrains.kotlin.plugin.compose` | Compose compiler. |
| `org.jetbrains.kotlin.jvm` | Builds `:core` as a plain Kotlin/JVM library. |
| `com.google.devtools.ksp` 2.3.10 | Runs Room's code generator. 2.3.1+ is needed for AGP 9's built-in Kotlin. |
| `androidx.room` (Room Gradle plugin) | Exports the schema JSON per version into `app/schemas` and feeds it to migration tests. |
| `org.gradle.toolchains.foojay-resolver-convention` (settings) | From the Android Studio template; lets Gradle fetch the JDK named in `gradle/gradle-daemon-jvm.properties`. |

## `:app` runtime

| Library | Why |
|---|---|
| `androidx.core:core-ktx` | Kotlin helpers for Android APIs. |
| `androidx.activity:activity-compose` | Hosts Compose in `MainActivity`; edge-to-edge. |
| `androidx.lifecycle:lifecycle-runtime-ktx` | Lifecycle-aware coroutines. |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | `ViewModel`s and `viewModel { }` factories in Compose. |
| `androidx.lifecycle:lifecycle-runtime-compose` | `collectAsStateWithLifecycle`: stops collecting when the screen is not visible. |
| `androidx.room:room-runtime` 2.8.5 | SQLite through Room. Room 2.x chosen over 3.0; see DECISIONS.md. |
| `androidx.room:room-ktx` | Coroutines and `withTransaction` for Room. |
| `androidx.room:room-compiler` (KSP) | Generates the DAO code at build time. |
| `androidx.navigation:navigation-compose` | Tabs and menu screens, with back stack and per-tab saved state. |
| Compose BOM: `ui`, `ui-graphics`, `foundation`, `material3` | The UI toolkit. |
| `compose ui-tooling-preview` | `@Preview` annotations only; no runtime effect. |

## `:app` debug / test only

| Library | Why |
|---|---|
| `compose ui-tooling` (debug) | Android Studio previews and layout inspector. |
| `compose ui-test-manifest` (debug) | Needed by Compose UI tests. |
| `junit:junit` 4 | JVM unit tests. |
| `androidx.test.ext:junit` | Instrumented test runner (Room tests in M2+). |
| `androidx.test.espresso:espresso-core` | Kept from the template; brings the AndroidX test runner used by instrumented tests. |
| `compose ui-test-junit4` | Compose UI tests, if any are added. |
| `androidx.room:room-testing` | `MigrationTestHelper` for migration tests. |

## Version floors (constraints, not extra dependencies)

| Library | Why |
|---|---|
| `org.jetbrains.kotlinx:kotlinx-serialization-core` ≥ 1.9.0 | Already in the app through Navigation (1.7.x). Raised because `room-testing` needs 1.8+, and AGP makes instrumented tests use the app's versions. |

## `:core`

| Library | Why |
|---|---|
| Kotlin stdlib | Comes with the Kotlin plugin. |
| `junit:junit` 4 (test) | Unit tests. |

## Planned (added in the milestone that needs them)

- `kotlinx-coroutines-test` (M4, timer tests), if `runBlocking` stops being enough.
