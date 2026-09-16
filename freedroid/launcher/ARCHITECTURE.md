# FreeDroid Launcher — Architecture

**Status:** `:core` is implemented and tested. `:app` and `:uitest` are written
but **have never been compiled or run** — the Android SDK is absent from the
development environment.

---

## 1. Module structure

```text
freedroid/launcher/
├── core/      launcher-core   pure Kotlin/JVM   ✅ builds + tests here
├── app/       launcher-app    Android app       ❌ needs the Android SDK
└── uitest/    launcher-test   instrumented UI   ❌ needs SDK + a device
```

Gradle paths are `:core`, `:app` and `:uitest`; they correspond to the
`launcher-core` / `launcher-app` / `launcher-test` roles in the project brief.

`settings.gradle.kts` includes `:app` and `:uitest` **only when an Android SDK is
present**. Without one, `gradle build` succeeds and builds `:core` alone rather
than failing everything with an SDK error that hides real problems.

---

## 2. The dependency rule

```text
        :uitest  ──────────┐
                           ▼
        :app  ───────►  :core
     (Android)         (pure Kotlin, NO Android)
```

`:core` depends on nothing but the Kotlin and Java standard libraries. This is
enforced by the `verifyNoAndroidDependencies` Gradle task, which runs as part of
`check` and has been tested against a deliberately added Android dependency.

**Why this matters more than it looks.** `docs/architecture/OVERVIEW.md` §5.4
prohibits branching on device category. In `:core` that is not a rule anyone has
to remember — `isTablet()`, `Configuration.smallestScreenWidthDp`, `Display` and
every other device-identity API are simply **not on the classpath**. The
architectural rule is enforced by the dependency graph.

The second benefit is practical: every layout, search and catalogue decision is a
pure function, so all of it is testable in milliseconds with no device — which
matters when the environment has no emulator and never will have one.

`scripts/check-launcher-constraints.sh` additionally verifies, from outside the
build, that no device-category check, prohibited permission, reflection, or
signing configuration has crept in anywhere.

---

## 3. Adaptive architecture

```text
   Android side                  │  Platform-agnostic (:core)
   ───────────────────────────── │ ─────────────────────────────
   LauncherActivity              │
        │  setContent            │
        ▼                        │
   LauncherRoot                  │
     BoxWithConstraints ─────────┼──►  WindowGeometry(widthDp, heightDp)
     (the window's own size)     │              │
                                 │              ▼
                                 │       LayoutPolicy.decide()
                                 │              │
                                 │              ▼
   HomeScreen / AppDrawer  ◄─────┼───────  LauncherLayout
   render what it says           │   hotseat position + capacity,
                                 │   pane mode, workspace grid,
                                 │   drawer columns, search row
```

### Why `BoxWithConstraints`

It reports the size of the space the launcher actually occupies — the app's
window, not the display. That is the correct input, and it is self-maintaining: a
split-screen resize, a fold, or a free-form window drag re-runs the constraints
and the layout follows. No configuration-change plumbing, no listener to forget
to unregister, no stale cached geometry.

> **Supersedes:** the earlier `WindowGeometryAdapter`, which read
> `WindowMetricsCalculator` directly. Both are correct at the root of the tree,
> but two ways to derive the same value can drift apart, and only one of them
> re-derives automatically on resize. The adapter was removed rather than left as
> an unused second path.

### Breakpoints

Mirrored from Jetpack WindowManager, pinned by `WindowSizeClassTest` and
`DrawerBreakpointTest` so the app-side adapter and `:core` cannot drift:

| Width | Class | Drawer columns | Height | Class | Workspace rows |
| --- | --- | --- | --- | --- | --- |
| < 600dp | COMPACT | 4 | < 480dp | COMPACT | 3 |
| 600–839dp | MEDIUM | 6 | 480–899dp | MEDIUM | 5 |
| ≥ 840dp | EXPANDED | 8 | ≥ 900dp | EXPANDED | 6 |

`DrawerBreakpointTest` asserts the column count changes at **exactly two widths
across the entire 201–2000dp range**, so a resize cannot cause the grid to reflow
anywhere unexpected, and that column count is **independent of height**, so
opening the keyboard cannot reflow the grid.

---

## 4. Package discovery

```text
LauncherApps.getActivityList()      Android, in :app
        │
        ▼
PackageAppSource  ──────────────►  AppEntry (pure value)
   translates platform types             │
   into :core types, on Dispatchers.IO   ▼
        │                          AppCatalog  (immutable snapshot)
        │                                │
LauncherApps.Callback ──► PackageChange ─┘
   Added / Updated / Removed / Unavailable
```

`LauncherApps` is the supported public API for enumerating launchable
activities. Nothing is hard-coded and nothing privileged is used.

**Immutability is the concurrency strategy.** Package events arrive on a binder
thread while the UI reads the catalogue on the main thread. Handing out a new
snapshot per change means neither side needs a lock and the UI can never observe
a half-applied update. It also makes every state transition a pure function, so
package-event handling is unit-tested off-device.

Event handling, all covered by `AppCatalogTest`:

| Event | Behaviour |
| --- | --- |
| Application added | Entries inserted |
| Application removed | All entries for the package dropped |
| Application updated | Entries for the package **replaced wholesale** |
| Package unavailable | Entries dropped; expected to return |
| Unchanged | The **same instance** is returned, so the UI can skip re-rendering |

The update case is the one a merge-based implementation gets wrong. An update can
add or remove launcher activities; merging would leave entries pointing at
activities that no longer exist, which then fail when tapped.

### Launch failure handling

```text
AppLauncher.launch()  ──►  LaunchOutcome  ──►  LaunchRecovery.actionFor()
  catches platform            Success              NONE
  exceptions                  ActivityNotFound     REFRESH_CATALOG
                              PackageUnavailable   NOTIFY_USER
                              PermissionDenied     NOTIFY_USER
                              Failed               NOTIFY_USER
```

`ActivityNotFound` proves the snapshot is stale — the user tapped something that
no longer exists — so the catalogue is rebuilt. `PermissionDenied` is Android
enforcing its own rules: reported, never worked around. A test asserts that **no
failure outcome maps to silence**, because a tap that does nothing is the worst
possible outcome.

---

## 5. Search architecture

Search lives entirely in `:core`, independent of the UI, so ranking behaviour is
unit-testable.

```text
query ──► TextNormalizer ──► AppSearcher.match() per entry ──► ranked results
          lowercase                    │
          strip accents                ▼
          collapse space        MatchQuality
                                EXACT > PREFIX > WORD_PREFIX >
                                INITIALS > SUBSTRING > SUBSEQUENCE
```

| Quality | Example |
| --- | --- |
| EXACT | "maps" → **Maps** |
| PREFIX | "ma" → **Maps** |
| WORD_PREFIX | "maps" → Google **Maps** |
| INITIALS | "gm" → **G**oogle **M**aps |
| SUBSTRING | "lock" → C**lock** |
| SUBSEQUENCE | "gml" → **G**oogle **M**ai**l** |

Design points:

- **Normalised labels are precomputed** (lazily, per entry) so filtering does not
  renormalise every label on every keystroke.
- **Ties break deterministically** — quality, then label length, then normalised
  label, then key. Non-deterministic ordering looks like a rendering bug and is
  miserable to reproduce.
- **Initials do not apply to single-word labels**, or every one-letter query
  would match nearly everything.
- **A blank query returns everything.** An empty search box in a drawer means
  "show all", not "show none". A non-blank query with no matches returns an empty
  list, which is what drives the drawer's no-results state.
- `lowercase()` is locale-invariant, so a Turkish locale cannot map "I" to a
  dotless i and silently break search for those users.

---

## 6. Security boundaries

**The launcher is not a privileged component and must not become one.**

| Property | Position |
| --- | --- |
| Permissions | `QUERY_ALL_PACKAGES` only — visibility, not capability |
| Root | None. No `su`, no shell, no `Runtime.exec` |
| Hidden APIs | None. No reflection anywhere |
| Filesystem | No storage permission of any kind |
| Network | **No `INTERNET` permission** — a compromised launcher cannot exfiltrate |
| Overlays | No `SYSTEM_ALERT_WINDOW` — it is a phishing primitive |
| Installing | No `INSTALL_PACKAGES`, no `REQUEST_INSTALL_PACKAGES` |
| Launching | `LauncherApps.startMainActivity` — the ordinary public path |
| Signing | No signing config; platform signing happens in the AOSP build |

Every permission the launcher holds is one an attacker inherits if the launcher
is compromised, so the manifest argues each one in a comment rather than
accumulating them silently.
`scripts/check-launcher-constraints.sh` fails if the permission list grows.

---

## 7. Performance

| Hazard | Mitigation |
| --- | --- |
| Icon loading | Suspending, on `Dispatchers.IO`; nothing decodes on the main thread |
| Large drawers | Icons requested only as tiles enter composition |
| Icon memory | Bounded `LruCache` (256 entries) — holding every icon is a real problem on a low-RAM device |
| Repeated PM queries | Package events applied as deltas; a full re-query only when a launch proves the catalogue stale |
| Recomposition | Stable `key` per grid item; layout is a pure function of constraints |
| Search cost | Normalised labels precomputed once per entry |

Deliberately **not** done yet: disk icon cache, bitmap pooling, pre-scaling,
paging. Those are worth adding once there is a device to measure on — the
current choices are reasoned, not measured, and that distinction is recorded
rather than glossed over.

---

## 8. Accessibility

Built in from the first version, not deferred:

- Each app tile is **one** focusable node with `Role.Button` and a
  `contentDescription` of the app label, so a screen reader announces
  "Gmail, button" instead of reading an icon and a label as unrelated nodes.
- Icons are marked decorative (`contentDescription = null`) — the tile already
  carries the name, and a second announcement is noise.
- Touch targets are at least **48dp**, Android's documented minimum.
- Labels use Material type styles, so text **scales with the user's font-size
  setting** rather than being pinned to fixed sp values.
- The search field reports its live result count to the accessibility tree, which
  is the feedback a sighted user gets free from watching the grid change.
- Hardware keyboard input works; Enter dismisses the IME rather than submitting,
  because results are already filtered live.
- `safeDrawingPadding()` keeps content clear of bars, cutouts and the IME with no
  hard-coded insets.

---

## 9. Future AOSP integration

**Not integrated, and no AOSP build files exist yet.** There is deliberately no
`Android.bp` in this project: an unvalidatable build file that looks real is
worse than none.

The planned integration point, for Phase 4:

```text
# device/freedroid/common/freedroid_common.mk
PRODUCT_PACKAGES += FreeDroidLauncher

# Replaces Launcher3 in the FreeDroid product. A product configuration change,
# NOT a modification of AOSP source.
```

This requires, in Phase 4:

1. An `Android.bp` declaring an `android_app` module, built by Soong against the
   platform SDK instead of Gradle.
2. A decision on how `:core` is consumed — most likely a `java_library` Soong
   module built from the same sources, so Gradle and Soong stay in step.
3. Removing Launcher3 from the product's package list.
4. Platform signing, performed by the AOSP build.

The Gradle project remains the development and unit-test environment; Soong
becomes the platform build. Keeping both means the pure logic stays testable on a
laptop without a 400 GB AOSP checkout.

---

## 10. Testing status

See `docs/development/TESTING.md` for the full picture.

| Suite | Where | Status |
| --- | --- | --- |
| `:core` unit tests | JVM | ✅ **80 passing** |
| `verifyNoAndroidDependencies` | Gradle | ✅ passing, negative-tested |
| `check-launcher-constraints.sh` | shell | ✅ 10 checks passing, negative-tested |
| Gradle configuration of `:app` / `:uitest` | Gradle | ✅ configures cleanly on AGP 8.11.1 |
| Android static checks (`check-android-static.sh`) | shell | ✅ 12 checks passing, negative-tested |
| `:app` compilation | Android SDK | ⛔ **blocked — no SDK** |
| `:app` unit tests | Android SDK | ⛔ **blocked — no SDK** |
| `:uitest` instrumented tests | SDK + device | ⛔ **blocked — no SDK, no KVM** |

Nothing in `:app` or `:uitest` has been executed. Those rows are blocked, not
passing, and are not counted anywhere as evidence.


---

## 11. Toolchain versions

| Component | Version | Note |
| --- | --- | --- |
| Android Gradle Plugin | **8.11.1** | AGP 8.7.x supports `compileSdk` 35 at most and hard-errors on 36 |
| Gradle | 8.14.3 | Satisfies AGP 8.11's requirement of 8.13+ |
| Kotlin | 2.0.21 | Unchanged — `:core` and its 80 tests already build on it |
| Compose compiler | 2.0.21 | Ships with Kotlin; applied via `org.jetbrains.kotlin.plugin.compose` |
| Compose BOM | 2025.06.00 | Manages all `androidx.compose.*` versions |
| `compileSdk` / `targetSdk` | 36 | Matches the FreeDroid Android 16 baseline |
| `minSdk` | 33 | |
| JVM target | 17 | Both `:core` and `:app` |

### Required Android SDK components

Named by AGP itself when the build is pointed at an empty SDK directory:

```text
platforms;android-36      Android SDK Platform 36
build-tools;35.0.0        Android SDK Build-Tools 35   (AGP 8.11.1's default)
cmdline-tools;latest      to run sdkmanager
```

Plus SDK licence acceptance. **None of these is installed**, which is the sole
remaining blocker to compiling `:app`.
