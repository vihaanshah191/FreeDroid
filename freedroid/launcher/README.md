# FreeDroid Launcher

The FreeDroid home screen, developed as a standalone Gradle project so the logic
can be built and tested without an AOSP tree. Phase 4 folds it into the platform
build via `PRODUCT_PACKAGES`, replacing Launcher3.

**Status:** the adaptive policy, search, sorting and catalogue are implemented
and tested — **80 passing tests**. The Android application is written but **has
never been compiled or run**: the Android SDK is absent from this development
environment.

See [ARCHITECTURE.md](ARCHITECTURE.md) for the design.

---

## Modules

| Gradle | Role | Kind | Builds here? |
| --- | --- | --- | --- |
| `:core` | launcher-core | Pure Kotlin/JVM | ✅ **Yes** — 80 tests passing |
| `:app` | launcher-app | Android application | ❌ Needs the Android SDK |
| `:uitest` | launcher-test | Instrumented UI tests (`com.android.test`) | ❌ Needs SDK **and** a device |

`:app` and `:uitest` are included only when an Android SDK is present, so
`gradle build` works here and builds `:core` alone.

---

## Build and test

```bash
cd freedroid/launcher

gradle :core:test     # 80 unit tests
gradle :core:check    # tests + the no-Android-dependency guard
gradle build          # :core only, unless ANDROID_HOME is set

# From the repository root — no SDK needed for either
./scripts/check-launcher-constraints.sh   # 10 architecture/security constraints
./scripts/check-android-static.sh         # 12 resource/manifest/module checks
```

### To compile `:app` you need

```text
platforms;android-36      Android SDK Platform 36
build-tools;35.0.0        Android SDK Build-Tools 35
cmdline-tools;latest      to run sdkmanager
```

(AGP named these itself when pointed at an empty SDK directory.) Set
`ANDROID_HOME` and `:app` and `:uitest` join the build automatically.

Verified on Gradle 8.14.3 / OpenJDK 21. Requires Maven Central and Google Maven.

To build `:app`, set `ANDROID_HOME` (or `sdk.dir` in `local.properties`) on a
host with the SDK installed.

---

## What works today

Everything in `:core`, all of it tested:

| Area | Implemented |
| --- | --- |
| Adaptive layout policy | Hotseat position + capacity, pane mode, workspace grid, drawer columns, search row — all a pure function of window size |
| Application catalogue | Immutable snapshot; add / remove / update / unavailable handled |
| Search | Case- and accent-insensitive, six ranked match qualities, deterministic ordering |
| Sorting | Alphabetical, reverse, by package — all total and stable |
| Launch failure policy | Five outcomes mapped to recovery actions; none of them silent |

## What is written but unverified

Everything in `:app`: the Compose UI (home screen, app drawer, search field, app
grid, tiles, hotseat), package discovery via `LauncherApps`, the icon cache, the
launcher activity and manifest. **None of it has been compiled.**

---

## The adaptive rule

Layout is a pure function of **window** size. Not display size, not device model.

| Window | Width class | Hotseat | Panes | Drawer cols |
| --- | --- | --- | --- | --- |
| Phone portrait (411×891) | COMPACT | bottom | single | 4 |
| Foldable closed (360×816) | COMPACT | bottom | single | 4 |
| Split-screen (411×400) | COMPACT | bottom | single | 4 |
| Foldable open (674×841) | MEDIUM | bottom | single | 6 |
| Tablet portrait (800×1280) | MEDIUM | bottom | single | 6 |
| Phone landscape (891×411) | **EXPANDED** | side | dual | 8 |
| Tablet landscape (1280×800) | EXPANDED | side | dual | 8 |
| Desktop (1920×1080) | EXPANDED | side | dual | 8 |

**The row worth staring at:** a large phone in landscape is an EXPANDED window
and gets the same treatment as a tablet, because at 891dp wide that is what it
is. The first version of that test asserted a compact layout and failed —
correctly. Any code reasoning from "this is a phone" would lay out an 891dp
window as if it were narrow and waste most of it.

There is no `isTablet()` anywhere, and there cannot be: `:core` has no Android
dependency, so device-identity APIs are not on its classpath at all.

---

## Tests

**80 passing**, all in `:core`, all runnable without a device:

| Suite | Tests | Covers |
| --- | --- | --- |
| `AppSearcherTest` | 18 | Match qualities, ranking, determinism, empty states, large-catalogue speed |
| `AppCatalogTest` | 16 | Package add/remove/update/unavailable, immutability, multi-profile |
| `LayoutPolicyTest` | 14 | Per-geometry layouts, ~46,000-point sweep, monotonicity, invariants |
| `AppSorterTest` | 7 | Ordering, accents, stability, totality |
| `DrawerBreakpointTest` | 7 | Exact breakpoints; columns change at exactly two widths; height-independent |
| `TextNormalizerTest` | 7 | Case, accents, locale-invariance, separators |
| `LaunchRecoveryTest` | 6 | Every failure path; none silent |
| `WindowSizeClassTest` | 5 | Breakpoint boundaries, validation |

Highlights:

- `identical window sizes produce identical layouts regardless of device` — a
  tablet in split-screen and a phone produce byte-identical layouts.
- `column count changes at exactly two widths across the whole range` — a resize
  cannot reflow the grid anywhere unexpected.
- `no failure outcome is silently ignored` — a tap that does nothing is the worst
  outcome, so every failure has a user-visible response.

**Blocked, not passing:** `:app` compilation, `:app` unit tests, and all
`:uitest` instrumented tests. No Android SDK, and no KVM for an emulator.

---

## Security posture

- **One permission:** `QUERY_ALL_PACKAGES`. Visibility, not capability.
- **No `INTERNET`** — a compromised launcher cannot exfiltrate.
- No root, no reflection, no hidden APIs, no storage access, no overlays.
- No `INSTALL_PACKAGES` or `REQUEST_INSTALL_PACKAGES`.
- No signing configuration; platform signing happens in the AOSP build.
- All dependency versions pinned; no dynamic versions.

Enforced by `scripts/check-launcher-constraints.sh` (10 checks), which has been
negative-tested against deliberately planted violations.

---

## Not implemented

Workspace pages, folders, widgets, drag and drop, app shortcuts, notification
badges, work-profile tab, icon packs, predictive back, wallpaper *selection*,
Soong (`Android.bp`) build files. All Phase 4.
