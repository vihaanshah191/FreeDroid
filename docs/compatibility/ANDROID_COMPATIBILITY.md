# FreeDroid — Android Compatibility Contract

**Phase:** 0 (Environment + Architecture)
**Status:** Design commitment. **No compatibility testing has been performed.**
**Planned baseline:** AOSP `android-16.0.0_r4` — *planned, not synced*

---

## 1. The contract

> An Android APK built against the public SDK, which runs on a stock Android 16
> device, runs on FreeDroid — unmodified, unrecompiled, unrepackaged.

This is not a best-effort aspiration. It is the constraint that makes the project
worth building: an operating system offering distribution freedom is useless if
the applications people want to distribute freely do not run on it.

FreeDroid is **AOSP-derived**, not an independent Android-compatible runtime.
Applications execute on the same ART, the same framework, the same Binder, and
the same kernel interfaces as on any other Android device. Compatibility is
inherited by *not breaking* it, which is far more achievable than re-creating it.

---

## 2. The protected surface

**These are unmodified by default.** Changing any of them requires the analysis
and sign-off in §6.

### 2.1 Android SDK APIs

| Commitment | Detail |
| --- | --- |
| Public SDK | Complete and unmodified. No removals, no signature changes, no behavior changes. |
| `@SystemApi` / hidden APIs | Hidden-API restrictions preserved at AOSP levels. **Not loosened for convenience** — loosening creates a surface that diverges from Android and that apps come to depend on. |
| Deprecations | Follow AOSP. FreeDroid does not deprecate or remove ahead of upstream. |
| New FreeDroid APIs | Ship in a **separate SDK library**, never merged into `framework.jar`. An app ignoring it sees stock Android. |

### 2.2 ART — Android Runtime

| Commitment | Detail |
| --- | --- |
| Runtime | Unmodified. No alternate or additional runtime. |
| Bytecode contract | Unmodified. DEX format, verifier, and semantics unchanged. |
| Compilation | AOSP's AOT/JIT strategy. No custom compilation pipeline. |
| App transformation | None. Applications are not recompiled or repackaged for FreeDroid. |

ART is also a security boundary — bytecode verification, W^X, and JIT hardening
live here. Modifying it means re-validating those properties for no benefit.

### 2.3 Binder and IPC

| Commitment | Detail |
| --- | --- |
| Binder | Unmodified transaction semantics, threading model, and death notification. |
| AIDL | Unmodified. Stable AIDL contracts preserved. |
| `ContentProvider` | Unmodified permission and URI-grant semantics. |
| `PendingIntent` | Unmodified, including mutability rules. |
| `BroadcastReceiver` | Unmodified registration, ordering, and delivery. |

FreeDroid services are ordinary Binder services with permission-guarded
interfaces — they use the IPC mechanism, they do not alter it.

### 2.4 PackageManager

| Commitment | Detail |
| --- | --- |
| Installation | Unmodified. One path: `PackageInstaller` → `PackageManagerService`. |
| Signature verification | Unmodified. v2/v3/v3.1, v3 rotation, update continuity. |
| UID assignment | Unmodified, including `sharedUserId` legacy handling. |
| Queries | Unmodified package visibility rules. |
| Permissions | Unmodified declaration, grant, and enforcement. |

**FreeDroid adds no second install path and no source-conditional behavior.**
Provenance is carried through `setPackageSource()` — an existing AOSP mechanism.

### 2.5 ActivityManager

Unmodified: process lifecycle and priority, task and back-stack management,
background execution limits, Doze and App Standby, foreground-service types,
`onTrimMemory` and low-memory handling.

### 2.6 WindowManager

Unmodified: window types and z-ordering, multi-window and split-screen, activity
embedding, display and configuration management, insets and window metrics.

FreeDroid's adaptive UI uses **public window APIs**. It does not alter window
management.

### 2.7 Android permissions

Unmodified: the runtime permission model, protection levels, grant and revoke
flows, one-time grants, auto-revocation and hibernation, `targetSdk`-based
behavior, and the privileged permission allowlist mechanism.

**FreeDroid adds no new mandatory permission for existing functionality and no
additional permission check on an existing API.** A custom permission framework
would be a second enforcement point that can disagree with the first.

### 2.8 Intents

Unmodified: implicit and explicit resolution, intent filters and priority, package
visibility filtering, `startActivity`/`startService`/`sendBroadcast` semantics,
and intent redirection protections.

### 2.9 Application lifecycle

Unmodified: activity, fragment, service, and process lifecycle; configuration
change handling; saved-instance-state; `Application` callbacks.

### 2.10 Java / Kotlin compatibility

Unmodified: the `java.*` and `javax.*` class library at AOSP's level, Kotlin
stdlib interoperability, AndroidX compatibility, `desugar` behavior, and
reflection semantics.

### 2.11 NDK / native ABI compatibility

| Commitment | Detail |
| --- | --- |
| ABIs | `arm64-v8a`, `x86_64` per the NDK ABI contract |
| Bionic | Unmodified libc/libm/libdl behavior and symbol versioning |
| NDK APIs | Full stable native API surface preserved |
| JNI | Unmodified semantics |
| Symbols | No removals, no signature changes |
| 16 KB pages | Android 16 page-size requirements honored on supported targets |

---

## 3. What FreeDroid adds, and why it is additive

| Addition | Compatibility impact |
| --- | --- |
| FreeDroidLauncher | **None.** A launcher is an app implementing `HOME`. |
| SystemUI overlays | **None to apps.** Visual and QS tile changes. |
| Settings injection | **None.** Standard injection mechanism. |
| FreeDroid Store | **None.** An ordinary app with `REQUEST_INSTALL_PACKAGES`. |
| `SourceTrustService` | **None.** Apps do not call it; affects installer UI copy only. |
| `UpdateService` | **None.** Apps do not call it. |
| FreeDroid SDK | **Additive.** Separate library. An app that ignores it sees stock Android; an app that uses it will not run on stock Android, which is that app's informed choice. |
| RRO branding | **None.** Resource values only. |

**Design rule:** FreeDroid features sit *beside* the application runtime path,
never inside it. A feature needing a check inserted into `PackageManagerService`
is categorically more expensive than one adding a service alongside it — and that
cost belongs in the design discussion, not the code review.

---

## 4. Compatibility risks introduced by modifications

Each row is a risk **created by FreeDroid's own planned work**. None has
materialized, because none of the work exists.

| # | Planned modification | Compatibility risk | Severity | Control |
| --- | --- | --- | --- | --- |
| C1 | SystemUI patches (Phase 5) | Notification, QS, or shade behavior diverging from AOSP; apps relying on standard notification presentation | 🟠 High | Overlay-first; each patch justified individually; patch count tracked as a drift signal |
| C2 | Launcher replacement (Phase 4) | Widget hosting, shortcut, and `HOME` intent behavior differing from Launcher3 | 🟡 Medium | Implement the full launcher contract; test widget and shortcut APIs |
| C3 | FreeDroid SDK (Phase 6) | Apps depending on it stop running on stock Android; if merged into `framework.jar`, it would widen the core surface | 🟡 Medium | Separate library, never in `framework.jar`; document the portability consequence |
| C4 | Additive SELinux policy (Phase 6) | An over-broad or badly scoped domain denying an operation apps legitimately perform | 🟠 High | Additive only; never weaken AOSP domains; SEC-15 denial audit |
| C5 | Install-flow changes (Phase 7) | Extra friction or a modified confirmation flow breaking installer apps or automation | 🟠 High | Use `PackageInstaller` unmodified; add provenance display only, never a second path |
| C6 | Permission preview UI (Phase 7) | A parallel permission surface diverging from `PermissionController` | 🟠 High | Display-only; **no enforcement logic**; enforcement stays entirely in AOSP |
| C7 | RRO config overrides (Phase 4) | Overriding a `config.xml` value that framework code depends on | 🟡 Medium | Review each override against its framework consumers |
| C8 | OTA / `update_engine` config (Phase 8) | Partition or slot changes breaking app data migration across updates | 🟠 High | Follow AOSP A/B conventions; test update-with-data-preserved (APP-12) |
| C9 | Any `patches/` entry | Direct behavior change in framework code apps depend on | 🔴 Critical | Full §6 analysis; security review; lead sign-off |
| C10 | Forking an AOSP component | Permanent divergence; upstream fixes stop arriving automatically | 🔴 Critical | §6 analysis, ADR, lead sign-off. Currently zero forks, and keeping it there is a goal. |
| C11 | Google-free configuration | Apps depending on Play Services degrade or fail | 🟡 Medium | Expected and by design; **label** affected apps before installation (§7) |
| C12 | Honest attestation reporting | Apps enforcing stock-OS attestation refuse to run | 🟡 Medium | Accepted cost. FreeDroid will not spoof device state (§7.3). |

---

## 5. The compatibility rule

> **Any FreeDroid modification capable of breaking ordinary Android applications
> requires explicit compatibility analysis and testing.**

"Capable of breaking" is judged by reachability, not intent. If a change touches
the protected surface in §2, or alters behavior an application could observe, it
is in scope — regardless of how unlikely breakage seems.

When it is unclear whether a change qualifies, **treat it as though it does.**
The analysis is cheap; a compatibility regression discovered by users is not.

---

## 6. Review gate

Required before implementing any change to §2, before adding any `patches/`
entry, and before forking any AOSP component.

### Required analysis

1. **Locate** the existing implementation and read it.
2. **Understand** its responsibilities and its callers.
3. **Identify compatibility impact:** which public APIs change behavior? Which app
   patterns break? Does it alter permission, lifecycle, or IPC semantics? Does it
   affect CTS?
4. **Identify security impact** — the §T3 questions in the security model.
5. **Identify upstream dependencies** — what else in AOSP relies on this?
6. **Justify** why an RRO, product config, or new module cannot achieve it.
7. **Scope** to the smallest reasonable change.
8. **Test** — build, boot, affected CTS modules, app compatibility corpus.
9. **Document** — an ADR for anything architecturally significant.

### Sign-off

| Change class | Required approval |
| --- | --- |
| RRO, product config, new module | Normal code review |
| Additive SELinux policy | Code review + security review |
| New system service or `signature` permission | Security review + lead sign-off |
| Patch to AOSP source | Compatibility analysis + security review + lead sign-off |
| Fork of an AOSP component | All of the above + ADR |

### Automatic rejection

No analysis makes these acceptable:

- Disabling or weakening package signature verification
- Bypassing permission checks
- Weakening the application sandbox
- Adding hidden or undocumented privileged APIs
- Changing public SDK API signatures
- Modifying ART's bytecode contract
- Loosening hidden-API restrictions beyond AOSP defaults
- Spoofing attestation or device integrity state

---

## 7. Google Play and GMS

### 7.1 AOSP does not include Google services

> **AOSP does not automatically include the Google Play Store or Google Mobile
> Services.** They are proprietary, separately licensed products, not part of the
> open-source platform.

FreeDroid does not add them, does not copy or redistribute proprietary Google
components, and does not bundle unofficial Google packages or reimplementations.

### 7.2 Two configurations

```text
FreeDroid Open                        FreeDroid GMS
├── AOSP                              ├── AOSP
├── FreeDroid components              ├── FreeDroid components
├── FreeDroid Store                   ├── FreeDroid Store
├── direct APK installation           ├── direct APK installation
└── third-party app stores            ├── third-party app stores
                                      ├── Google Play Store          [licensed]
   DEFAULT CONFIGURATION              ├── Google Play Services       [licensed]
   Fully functional                   └── other Google components    [licensed]
   No Google dependency
                                         ASPIRATIONAL — requires CTS/CDD
                                         compliance plus a Google licensing
                                         and certification agreement.
                                         NOT GUARANTEED.
```

**FreeDroid Open** is the default and must always be fully functional:
applications install, update, and run; notifications, location, and networking
work without any Google component.

**FreeDroid GMS** is aspirational. Google components may be present **only** where
licensing, certification, and technical requirements permit. **GMS approval is
not guaranteed** — it requires a commercial agreement with Google, on terms
Google sets, and may never be obtained. Nothing in this project should be planned
on the assumption that it will be.

### 7.3 Known incompatibilities on FreeDroid Open

| Dependency | Effect |
| --- | --- |
| FCM push | No push notifications. Often a **silent** failure, which is worse than a loud one. |
| Play Billing | In-app purchases fail. |
| Play Integrity / SafetyNet | Attestation fails; some banking, payment, and media apps refuse to run. |
| Maps SDK | Map surfaces fail to load. |
| Play Services location | Falls back to AOSP `LocationManager` where the app supports it; otherwise degraded. |
| Play Games, Ads, Auth | Non-functional. |

**Obligations this creates:**

1. The Store **labels** applications with these dependencies **before**
   installation. Discovering it afterward is a user-hostile failure.
2. FreeDroid does not claim these applications "work."
3. Compatibility shim layers (microG-style) may be *evaluated*. They carry their
   own security and legal considerations and are explicitly **not** part of the
   default configuration.

**On attestation:** apps enforcing "stock OS from an approved vendor" will refuse
to run, and an unlocked bootloader reports ORANGE and fails most policies. **This
cannot be fixed without lying about device state, which FreeDroid will not do.**
Spoofing would undermine the same integrity guarantees the security model depends
on in §T5, and would deceive the relying party. It is a real cost of the
project's goals, stated as one.

**Also:** no Play means no Play system updates, so FreeDroid must deliver APEX
updates itself. See the OTA phase in the roadmap.

### 7.4 Future milestone

```text
Android compatibility → CTS/CDD compliance → GMS licensing/certification process
```

Sequential; each step is a prerequisite for the next.

| Step | Meaning | Status |
| --- | --- | --- |
| Android compatibility | Ordinary APKs run correctly | **Design commitment, untested** |
| CTS/CDD compliance | Formal conformance demonstrated by test results | Phase 9–12 objective |
| GMS licensing / certification | A commercial agreement with Google | **Not pursued. Not guaranteed. May never be available.** |

---

## 8. Third-party applications

FreeDroid intentionally supports:

```text
FreeDroid Store          first-party applications and updates
Third-party stores       F-Droid-style repositories and other stores
Direct APK installation  user-approved, from a file or a browser
```

**All applications, from all sources, remain subject to** Android package
management, package and signature validation, the permission model, the
application sandbox, SELinux, and every other platform security control.

**Direct APK installation bypasses none of these.** "Direct" describes where the
file came from, not how it is installed.

**No privileged universal APK installation API will be created.** Such an API
would be the most valuable target on the device and would make the sandbox
negotiable.

---

## 9. Compatibility testing

Detail in [`TESTING.md`](../development/TESTING.md). Gates:

| Gate | Requirement |
| --- | --- |
| **CTS baseline** | Established on **unmodified AOSP** in Phase 1. "CTS passes" is meaningless without knowing what the baseline passed. |
| **CTS regression** | Every FreeDroid build compared against that baseline. Any new failure is a release blocker. |
| **Real-app corpus** | A fixed set of third-party APKs: install, launch, permissions, update, uninstall, each release. |
| **NDK ABI** | Native sample apps verifying ABI stability. |
| **CTS Verifier** | Manual hardware tests, Phase 10+. |

**CDD conformance** is the technical target. Formal CTS certification is a
separate commercial and legal matter (§7.4), not required for technical
compatibility.

---

## 10. Adaptive UI compatibility

One codebase, phone and tablet. Mechanisms: resource qualifiers,
`WindowSizeClass`, `WindowMetricsCalculator`, activity embedding, multi-window,
and `PRODUCT_CHARACTERISTICS` only where hardware genuinely differs.

**Prohibited in FreeDroid UI code:** `if (isTablet())` and equivalents. Branch on
current **window** metrics, never device category. Foldables change category at
runtime, making this a correctness requirement rather than a style preference.

Android 16 ignores orientation and resizability restrictions on large screens for
apps targeting SDK 36. FreeDroid must not reintroduce restrictions AOSP removed —
doing so would make FreeDroid *less* compatible than stock on large screens.
