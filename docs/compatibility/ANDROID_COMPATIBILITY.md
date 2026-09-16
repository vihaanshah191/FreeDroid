# FreeDroid — Android Compatibility

**Status:** Design intent. No compatibility testing has been performed.
**Baseline:** AOSP `android-16.0.0_r4`

---

## 1. The commitment

An Android APK built against the public SDK, which runs on a stock Android 16
device, runs on FreeDroid — unmodified, unrecompiled, unrepackaged.

This is not a best-effort goal. It is the constraint that makes FreeDroid worth
building: an OS offering distribution freedom is useless if the apps users want
to distribute freely do not run on it.

**Corollary that governs day-to-day work:** any change that could break an
ordinary Android app is a compatibility defect until proven otherwise, no matter
how appealing the feature attached to it.

## 2. The protected surface

These are unmodified by default. Changing any of them requires the review gate
in §5.

### Runtime and ABI

| Surface | Commitment |
| --- | --- |
| **ART** | Unmodified. No bytecode changes, no alternate runtime, no custom verifier. |
| **DEX format** | Unmodified. |
| **Native ABI** | arm64-v8a, x86_64 per the NDK ABI contract. No symbol changes. |
| **Bionic** | Unmodified. libc/libm/libdl behavior and symbol versioning preserved. |
| **NDK APIs** | Full stable native API surface preserved. |
| **JNI** | Unmodified semantics. |
| **16 KB page size** | Android 16 page-size requirements honored on supported targets. |

### Framework APIs

| Surface | Commitment |
| --- | --- |
| **Public SDK** | Complete and unmodified. No removals, no signature changes, no behavior changes. |
| **`PackageManager`** | Unmodified. Install, query, permission, and signature semantics are AOSP's. |
| **`ActivityManager`** | Unmodified. Process lifecycle, task management, background policy. |
| **`WindowManager`** | Unmodified. Window types, multi-window, activity embedding. |
| **Permissions** | Unmodified model. No new mandatory permissions for existing functionality; no additional permission checks on existing APIs. |
| **Intents** | Unmodified resolution, filtering, and implicit-intent semantics. |
| **Lifecycle** | Unmodified activity, service, and process lifecycle. |
| **Binder / IPC** | Unmodified. AIDL, `PendingIntent`, `ContentProvider`, `BroadcastReceiver`. |
| **Storage** | Scoped storage, `MediaStore`, SAF, Photo Picker — unmodified. |
| **`@SystemApi` / hidden APIs** | Hidden-API restrictions preserved at AOSP levels. **Not** loosened for convenience — loosening them would create a compatibility surface that diverges from Android and that apps could come to depend on. |

### Behavior

| Surface | Commitment |
| --- | --- |
| Signature verification | v2/v3/v3.1 schemes, v3 rotation, update continuity — AOSP behavior exactly. |
| Sandbox | UID/GID assignment, SELinux `untrusted_app` labeling — AOSP behavior. |
| `targetSdk` gating | AOSP enforcement preserved. |
| Notifications | Channels, `POST_NOTIFICATIONS`, importance, DND — semantics unchanged. |
| Background execution | Doze, App Standby, foreground-service types, background start limits — unchanged. |

## 3. What FreeDroid adds, and why it is additive

| Addition | Compatibility impact |
| --- | --- |
| FreeDroidLauncher | None. A launcher is an app. It implements `HOME` like Launcher3. |
| SystemUI overlays | None to apps. Visual and QS tile changes only. |
| Settings injection | None. Uses the standard injection mechanism. |
| FreeDroid Store | None. An ordinary app holding `REQUEST_INSTALL_PACKAGES`. |
| `SourceTrustService` | None. Apps do not call it. Affects installer UI copy only. |
| `UpdateService` | None. Apps do not call it. |
| FreeDroid SDK | **Additive only** — ships as a **separate library**, not merged into `framework.jar`. An app that ignores it sees stock Android. An app that uses it will not run on stock Android, which is the app's informed choice. |
| RRO branding | None. Resource values only, no API changes. |

**The design rule behind this table:** FreeDroid features sit *beside* the app
runtime path, never inside it. A feature that requires inserting a check into
`PackageManagerService` is a different and much more expensive kind of feature
than one that adds a service alongside it, and should be recognized as such at
design time rather than at review time.

## 4. Known and accepted incompatibilities

Stated plainly rather than discovered by users.

### 4.1 Google Play Services dependency

AOSP contains no Play Services. FreeDroid's default configuration is Google-free.
Apps depending on proprietary Google APIs will degrade or fail:

| Dependency | Effect on a Google-free FreeDroid |
| --- | --- |
| FCM push | No push notifications. Often a silent failure, which is worse than a loud one. |
| Play Billing | In-app purchases fail. |
| Play Integrity / SafetyNet | Attestation fails. Some banking, payment, and media apps refuse to run. |
| Maps SDK | Map surfaces fail to load. |
| Play Services Location (fused provider) | Falls back to AOSP `LocationManager` where the app supports it; otherwise degraded. |
| Play Games, Ads, Auth | Non-functional. |

**Obligations this creates:**

1. The Store must **label** apps with these dependencies before installation.
   Discovering it afterward is a user-hostile failure.
2. FreeDroid must not claim these apps "work."
3. Compatibility shim layers (microG-style) may be evaluated. They carry their own
   security and legal considerations and are explicitly **not** part of the
   default configuration.

### 4.2 Hardware feature dependencies

Apps requiring NFC, telephony, or specific sensors will not install or will
degrade on devices lacking them (e.g. a Wi-Fi-only tablet). This is standard
Android `<uses-feature>` behavior, not a FreeDroid defect.

### 4.3 Device attestation

Hardware key attestation reports FreeDroid's boot state and signing key. Apps
enforcing a policy of "stock OS from an approved vendor" will refuse to run. An
unlocked bootloader reports ORANGE and fails most attestation policies.

**This cannot be fixed without lying about device state**, which FreeDroid will
not do. Spoofing attestation would undermine the same integrity guarantees
FreeDroid depends on in §T5 of the security model, and would be dishonest to the
relying party. It is a real cost of the project's goals and is stated as such.

## 5. Review gate for compatibility-affecting changes

Required before implementing any change to the surfaces in §2, and before adding
any `patches/` entry or forking an AOSP component.

### Required analysis

1. **Locate** the existing implementation. Read it.
2. **Understand** its responsibilities and its callers.
3. **Identify compatibility impact:**
   - Which public APIs change behavior?
   - Which app patterns could break?
   - Does it alter permission, lifecycle, or IPC semantics?
   - Does it affect CTS?
4. **Identify security impact** — the questions in `SECURITY_MODEL.md` §T3.
5. **Identify upstream dependencies** — what else in AOSP relies on this?
6. **Justify** why an overlay, product config, or new module cannot achieve it.
7. **Scope** to the smallest reasonable change.
8. **Test** — build, boot, run affected CTS modules, run app compatibility tests.
9. **Document** — ADR for anything architecturally significant.

### Sign-off

| Change class | Required approval |
| --- | --- |
| RRO, product config, new module | Normal code review |
| Additive SELinux policy | Code review + security review |
| New system service or `signature` permission | Security review + lead sign-off |
| Patch to AOSP source | Compatibility analysis + security review + lead sign-off |
| Fork of an AOSP component | All of the above + ADR |

### Automatic rejection

No analysis will make these acceptable:

- Disabling or weakening package signature verification
- Bypassing permission checks
- Weakening the sandbox
- Adding hidden or undocumented privileged APIs
- Changing public SDK API signatures
- Modifying ART's bytecode contract
- Loosening hidden-API restrictions beyond AOSP defaults
- Spoofing attestation or device integrity state

## 6. Compatibility testing

Detail in [`TESTING.md`](../development/TESTING.md). Summary of the gates:

| Gate | Requirement |
| --- | --- |
| **CTS** | Run against every FreeDroid build. Any regression versus the unmodified AOSP baseline is a release blocker. |
| **Baseline comparison** | Phase 1 establishes unmodified-AOSP CTS results. Every subsequent run compares against it, because "CTS passes" means nothing without knowing what the baseline passed. |
| **Real-app corpus** | A set of popular F-Droid and third-party APKs, install/launch/permission/update/uninstall tested each release. |
| **NDK ABI** | Native sample apps verifying ABI stability. |
| **CTS Verifier** | Manual hardware tests on physical devices, Phase 10+. |

**CDD conformance** is the target for compatibility. Formal CTS certification is a
separate commercial and legal matter, deferred to Phase 12 and not required for
technical compatibility.

## 7. Adaptive UI compatibility

One codebase, phone and tablet (project requirement 6). Mechanisms:

| Mechanism | Purpose |
| --- | --- |
| Resource qualifiers (`sw600dp`, `w840dp`, `-land`, `-port`) | Layout selection |
| `WindowSizeClass` / `WindowMetricsCalculator` | Runtime layout decisions |
| Activity embedding (`SplitController`) | Two-pane list/detail on large screens |
| Multi-window, split-screen, freeform | Supported, not special-cased |
| `PRODUCT_CHARACTERISTICS` | Only where hardware genuinely differs |

**Prohibited in FreeDroid UI code:** `if (isTablet())` and equivalents. Branch on
current window metrics, never on device category. Foldables change category at
runtime, so this is a correctness requirement, not a style preference.

Android 16 additionally ignores orientation and resizability restrictions on
large screens for apps targeting SDK 36. FreeDroid's own apps must handle
arbitrary window sizes and configuration changes, and FreeDroid must not
reintroduce restrictions AOSP removed — doing so would make FreeDroid *less*
compatible than stock on large screens.
