# FreeDroid Launcher

The FreeDroid home screen, developed as a standalone Gradle project so it can be
built and tested without an AOSP tree. Phase 4 folds it into the platform build
via `PRODUCT_PACKAGES`, replacing Launcher3.

**Status: skeleton.** The layout policy is complete and tested. The Android
application module is authored but has never been compiled or run.

---

## Modules

| Module | Kind | Builds in this container? |
| --- | --- | --- |
| `:core` | Pure Kotlin/JVM | ✅ **Yes** — builds and tests today |
| `:app` | Android application | ❌ No — needs the Android SDK |

`settings.gradle.kts` includes `:app` only when an Android SDK is present, so
`gradle build` works here and simply builds `:core`. Without that, every command
would fail with an SDK error rather than an honest "not available here".

### Why the split

`:core` holds the adaptive layout policy and has **no Android dependency**. That
is the point, not an accident of packaging.

`docs/architecture/OVERVIEW.md` §5.4 prohibits branching on device category —
`isTablet()`, `smallestScreenWidthDp`, display size. In `:core` that rule is not
a convention anyone has to remember: the Android API surface is absent from the
classpath, so device-category APIs are **unreachable**. The architectural rule is
enforced by the dependency graph.

A Gradle task, `verifyNoAndroidDependencies`, keeps it that way and runs as part
of `check`. It has been tested against a deliberately added Android dependency
and correctly failed the build.

The second benefit is practical: the policy is a pure function, so every form
factor FreeDroid will ever run on is testable without a device — which matters
when the environment has no emulator.

```text
  Android                          │  Platform-agnostic
  ─────────────────────────────────┼────────────────────────────────
  LauncherActivity                 │
        │                          │
  WindowGeometryAdapter ───────────┼──► WindowGeometry (width/height dp)
  (the only boundary)              │          │
                                   │          ▼
                                   │    LayoutPolicy.decide()
                                   │          │
                                   │          ▼
                                   │    LauncherLayout
```

---

## Build and test

```bash
cd freedroid/launcher

gradle :core:test     # run the layout policy tests
gradle :core:check    # tests + the no-Android-dependency guard
gradle build          # :core only, unless ANDROID_HOME is set
```

Requires JDK 17+ and network access to Maven Central and Google Maven. Verified
on Gradle 8.14.3 / OpenJDK 21.

To build `:app`, set `ANDROID_HOME` (or `sdk.dir` in `local.properties`) on a
host with the Android SDK installed.

---

## The layout policy

`LayoutPolicy.decide(WindowGeometry) -> LauncherLayout` is a pure function of
**window** size. Not display size, not device model.

Breakpoints mirror Jetpack WindowManager:

| Width | Class | Height | Class |
| --- | --- | --- | --- |
| < 600dp | COMPACT | < 480dp | COMPACT |
| 600–839dp | MEDIUM | 480–899dp | MEDIUM |
| ≥ 840dp | EXPANDED | ≥ 900dp | EXPANDED |

Resulting layouts:

| Window | Width class | Hotseat | Panes | Grid |
| --- | --- | --- | --- | --- |
| Phone portrait (411×891) | COMPACT | bottom | single | 4×5 |
| Foldable closed (360×816) | COMPACT | bottom | single | 4×5 |
| Split-screen (411×400) | COMPACT | bottom | single | 4×3 |
| Foldable open (674×841) | MEDIUM | bottom | single | 6×5 |
| Tablet portrait (800×1280) | MEDIUM | bottom | single | 6×6 |
| Phone landscape (891×411) | **EXPANDED** | side | dual | 8×3 |
| Tablet landscape (1280×800) | EXPANDED | side | dual | 8×5 |
| Desktop (1920×1080) | EXPANDED | side | dual | 8×6 |

### The row worth staring at

A **large phone in landscape is an EXPANDED window** and gets the same treatment
as a tablet, because at 891dp wide that is what it is.

This was not the original assumption. The first version of the test asserted a
phone in landscape would get a compact layout, and it failed — correctly. Any
code reasoning from "this is a phone" would lay out a 891dp window as if it were
narrow and waste most of it. That single case is the clearest argument for the
whole design.

---

## Tests

19 tests, all passing. Beyond the per-geometry cases:

- **`identical window sizes produce identical layouts regardless of device`** —
  a tablet in split-screen and a phone produce byte-identical layouts. This is
  the test that justifies the architecture.
- **`unfolding changes the layout`** — window size changes mid-session without
  the device changing.
- **`every plausible window size yields a usable layout`** — sweeps ~46,000
  sizes from 200×200 to 2000×2000, catching discontinuities that named cases miss.
- **`workspace capacity is monotonic in window size`** — a bigger window never
  produces a smaller workspace.
- **`side hotseat only ever appears on wide landscape windows`** and
  **`dual pane only ever appears on expanded width`** — invariants asserted
  across the input space, not just at sample points.
- **`decide is pure`** — same input, same output, 100 times.

---

## Not implemented

- Workspace, hotseat, and app drawer rendering — Phase 4
- App list, icons, labels, folders, widgets — Phase 4
- Search and predictive back — Phase 4
- Wallpaper integration — Phase 4
- Soong (`Android.bp`) build for the AOSP tree — Phase 4
- Everything in `:app` is uncompiled and unverified

## Security notes

- **No signing configuration.** Platform signing happens in the AOSP build;
  release keys never live in this repository.
- **Minimal permissions.** `QUERY_ALL_PACKAGES` only, which a launcher genuinely
  needs to list apps. Not requested: `INSTALL_PACKAGES`,
  `REQUEST_INSTALL_PACKAGES`, `SYSTEM_ALERT_WINDOW`, storage, `INTERNET`. Every
  permission is one an attacker inherits if the launcher is compromised.
- **Pinned dependency versions.** No dynamic versions; they would make builds
  non-reproducible and widen the supply-chain surface.
