# FreeDroid — Architecture Overview

**Phase:** 0 (Environment + Architecture)
**Status:** Design intent. **Nothing described here has been built, booted, or measured.**
**Planned baseline:** AOSP `android-16.0.0_r4` — *planned, not synced*

---

## 1. Purpose

FreeDroid is a secure, Android-based operating system for smartphones and
tablets, built on one principle:

> **Freedom of application installation without sacrificing security.**

Users decide where their applications come from — the FreeDroid Store, a
third-party store, an F-Droid-style repository, or a downloaded APK. Every one of
those applications runs inside Android's sandbox, under Android's permission
model, validated by Android's package manager, with SELinux enforcing.

Distribution freedom and a strong security model are usually presented as a
trade. FreeDroid's position is that they are only in tension if distribution
freedom is implemented by weakening the platform. Implemented as *provenance and
user control around an unmodified installer*, it costs nothing in security.

### Goals

1. Broad Android application compatibility.
2. Multiple application distribution sources, none of them privileged over another.
3. Security as a first-class architectural constraint, never traded for convenience.
4. One codebase for phones and tablets.
5. Maintainability across future Android releases.
6. Full functionality without Google Play where technically possible.

### Non-goals

- A new application runtime.
- A replacement for Android's permission or security model.
- Root access for applications.
- Lock-in to any single application store, FreeDroid's own included.

---

## 2. Relationship to AOSP

> **FreeDroid is an AOSP-derived operating system. It is not an independent
> Android-compatible runtime, not a reimplementation of the Android API, and not
> a compatibility layer that runs Android applications on some other system.**

FreeDroid *is* Android, with a documented delta. Applications run on the same
ART, the same framework, the same Binder, and the same kernel interfaces they run
on with any other Android device. This is deliberate: a reimplemented runtime
would inherit endless compatibility problems and would have to re-earn every
security property AOSP already has.

### Derivation model

FreeDroid is maintained as an **overlay** on upstream AOSP, composed by `repo`
at sync time. Upstream source is never vendored into the FreeDroid repository.

```text
   upstream AOSP  (android.googlesource.com, ~1000 git repositories)
              │
              │  repo init -b android-16.0.0_r4   [PLANNED — not yet executed]
              │  repo sync
              ▼
   ┌──────────────────────────────────────────────┐
   │  build workspace  (scratch on the build host)│
   │                                              │
   │   upstream AOSP  +  FreeDroid overlay        │
   │                        ▲                     │
   └────────────────────────┼─────────────────────┘
                            │ .repo/local_manifests/freedroid.xml
                            │
                      FreeDroid repository  (this repo — the delta only)
```

Rationale and consequences:
[ADR-0001](decisions/ADR-0001-overlay-repository-structure.md).

### Baseline and update streams

| | |
| --- | --- |
| Planned baseline | `android-16.0.0_r4` (**planned — not synced, not pinned**) |
| Feature stream | `android-16.0.0_rN` platform tags → FreeDroid feature releases |
| Security stream | `android16-security-release` → security-only releases, independently shippable |

Two streams exist so that a security fix does not have to wait for a feature
release. That property survives only while the FreeDroid delta stays small enough
that a security merge does not drag feature work with it — which is why the
customization hierarchy in ADR-0001 is enforced rather than merely recommended.

---

## 3. System layering

```text
┌───────────────────────────────────────────────────────────────────┐
│  APPLICATIONS                                                     │
│  Third-party APKs · FreeDroid first-party apps                    │
│  Identical API surface. No privileged application class.          │
├───────────────────────────────────────────────────────────────────┤
│  FREEDROID UI LAYER                        [FreeDroid — Phase 4-5]│
│  Launcher · SystemUI extensions · Settings injection              │
│  One adaptive codebase: phone and tablet differ by window metrics │
├───────────────────────────────────────────────────────────────────┤
│  FREEDROID FRAMEWORK EXTENSIONS            [FreeDroid — Phase 6]  │
│  SourceTrustService · UpdateService · FreeDroid SDK               │
│  Additive, beside the framework — never inside it                 │
├───────────────────────────────────────────────────────────────────┤
│  ANDROID FRAMEWORK                         [AOSP — UNMODIFIED]    │
│  PackageManager · ActivityManager · WindowManager · Binder ·      │
│  PermissionController · Intents · lifecycle                       │
├───────────────────────────────────────────────────────────────────┤
│  ART — Android Runtime                     [AOSP — UNMODIFIED]    │
├───────────────────────────────────────────────────────────────────┤
│  NATIVE LAYER                              [AOSP — UNMODIFIED]    │
│  Bionic · NDK ABI · native libraries                              │
├───────────────────────────────────────────────────────────────────┤
│  HAL — Hardware Abstraction Layer          [AOSP + vendor]        │
│  AIDL/HIDL interfaces · Keystore/KeyMint · Gatekeeper             │
├───────────────────────────────────────────────────────────────────┤
│  LINUX KERNEL                              [AOSP/GKI + vendor]    │
│  SELinux · seccomp · UID isolation · dm-verity · FBE              │
├───────────────────────────────────────────────────────────────────┤
│  VERIFIED BOOT (AVB 2.0) · bootloader · hardware root of trust    │
└───────────────────────────────────────────────────────────────────┘
```

The layers marked **UNMODIFIED** are where application compatibility lives.

### 3.1 Android Framework — unmodified

The framework provides package management, process and activity lifecycle, window
management, permissions, and Binder IPC. FreeDroid does not modify it.

| Service | FreeDroid's relationship |
| --- | --- |
| `PackageManagerService` | **Unmodified.** All installation, signature verification, UID assignment, and permission granting flows through it. FreeDroid adds no second path. |
| `ActivityManagerService` | **Unmodified.** Process lifecycle and background policy are AOSP's. |
| `WindowManagerService` | **Unmodified.** FreeDroid's adaptive UI uses public window APIs; it does not alter window management. |
| `PermissionController` | **Unmodified.** FreeDroid surfaces and defaults Android's permission controls; it does not replace them. |
| Binder / IPC | **Unmodified.** FreeDroid services are ordinary Binder services with permission-guarded interfaces. |

**Design rule:** FreeDroid features sit *beside* the application runtime path,
never inside it. A feature requiring a check inserted into
`PackageManagerService` is a categorically more expensive feature than one that
adds a service alongside it, and that cost belongs in the design discussion, not
the code review.

### 3.2 ART — unmodified

The Android Runtime executes application bytecode.

- No changes to ART, the DEX format, the bytecode contract, or the verifier.
- No alternate or additional runtime.
- Applications are not recompiled, repackaged, or transformed for FreeDroid.

ART is also a security boundary — bytecode verification, W^X enforcement, and JIT
hardening all live here. Modifying it would mean re-validating those properties
with no corresponding benefit.

### 3.3 Linux kernel

The kernel enforces the security boundaries the rest of the model depends on:
SELinux, seccomp-bpf, UID/GID isolation, dm-verity, and File-Based Encryption.

| Aspect | Approach |
| --- | --- |
| Source | AOSP common kernel / GKI where available |
| Modification | **None planned.** A kernel change requires security review; kernel divergence is the most expensive kind. |
| GKI | Strongly preferred for physical devices — sharply reduces kernel maintenance |
| Vendor modules | Device-specific, supplied per target, and frequently the weakest link in the system |
| Security updates | Tracked with the platform security stream |

### 3.4 HAL — Hardware Abstraction Layer

The HAL is the boundary between the framework and device hardware. FreeDroid
consumes AIDL/HIDL HAL interfaces; it does not redefine them.

Security-relevant HALs:

| HAL | Provides | Dependency |
| --- | --- | --- |
| KeyMint / Keystore | Hardware-backed key storage and attestation | TEE, or StrongBox where present |
| Gatekeeper / Weaver | Credential verification, brute-force rate limiting | TEE |
| `boot` | Verified Boot state, rollback index | Bootloader |
| `secure_element` | Hardware-isolated secrets | Device-dependent |

**Every hardware-backed security property in this architecture depends on the
device providing it.** Cuttlefish emulates some and provides no real hardware
guarantee for any. This distinction is tracked per mechanism in
[`SECURITY_MODEL.md`](../security/SECURITY_MODEL.md).

---

## 4. FreeDroid components

All are **planned**. None exist.

### 4.1 FreeDroid framework extensions — Phase 6

Additive system services and an SDK, sitting beside the Android framework.

| Component | Responsibility | Exposure to ordinary apps |
| --- | --- | --- |
| `SourceTrustService` | Records per-source user trust decisions; supplies provenance to the installer UI | **None.** `signature`-guarded. Cannot install anything. |
| `UpdateService` | Update policy, staging, security-patch-level reporting | **None.** No app-reachable install path. |
| FreeDroid SDK | Optional APIs for FreeDroid-aware apps | Separate library, **never merged into `framework.jar`** |

Constraints:

- Each service runs in its own SELinux domain with minimal, reviewed policy.
- Each exposes a narrow, permission-guarded interface, or none.
- **No universal privileged API.** There is no FreeDroid interface through which
  an ordinary application performs a privileged operation on its own behalf.
- The SDK is a separate library so that an app ignoring it sees stock Android,
  and so that FreeDroid adds no surface to the core framework.

Every new system service is a permanent addition to the attack surface. The
service count is a number to keep low, not a feature to grow.

### 4.2 FreeDroid SystemUI — Phase 5

Notification shade, quick settings, lock screen, status bar, navigation.

**Overlay-first.** RROs and configuration wherever they suffice; SystemUI source
patches only where a component genuinely cannot be injected, each justified
individually. A growing patch count in this phase is the early signal that the
approach is drifting.

Adaptive by construction: status bar, shade, and navigation adapt to window size
class, not to a device category.

### 4.3 FreeDroid Launcher — Phase 4

Home screen, app drawer, search, widgets. Replaces Launcher3 via
`PRODUCT_PACKAGES` — a product configuration change, not a source modification.

Adaptive from the first commit: single-pane on COMPACT windows, multi-pane with a
navigation rail on EXPANDED. No device-category branching.

### 4.4 FreeDroid Store — Phase 7

The first-party application source. Architecturally, **an ordinary application**.

- Holds `REQUEST_INSTALL_PACKAGES`, like any other installer.
- **Never holds `INSTALL_PACKAGES`** — the privileged, no-confirmation permission.
- Has no verification shortcut, no signature exemption, no privileged install path.
- Is uninstallable or replaceable by the user.

This is the load-bearing decision of the whole design. If FreeDroid's own store
needed privileges a third-party store cannot have, the multi-source promise would
be decorative and the no-lock-in goal would be true only on paper. The cost is
real — the Store will feel marginally less seamless than a privileged store — and
it is the correct cost to pay.

### 4.5 Application distribution architecture — Phase 7

```text
  FreeDroid Store        Third-party store        Direct APK
  (first-party)          (F-Droid, other)         (file, browser, sideload)
        │                       │                       │
        │   each holds REQUEST_INSTALL_PACKAGES, or is the user-selected
        │   default installer. Nothing more. No source is privileged.
        ▼                       ▼                       ▼
  ┌──────────────────────────────────────────────────────────────┐
  │  SourceTrustService                      [FreeDroid, Ph. 6]  │
  │  Records which sources the user trusts. Supplies provenance  │
  │  to the confirmation UI. ADVISORY ONLY — it cannot grant     │
  │  install capability to anything.                             │
  └──────────────────────────────────────────────────────────────┘
        │                       │                       │
        ▼                       ▼                       ▼
  ┌──────────────────────────────────────────────────────────────┐
  │  PackageInstaller                        [AOSP, UNMODIFIED]  │
  │  Session-based install · setPackageSource() carries          │
  │  provenance · user confirmation · no bypass path for anyone  │
  └──────────────────────────────────────────────────────────────┘
        │
        ▼
  ┌──────────────────────────────────────────────────────────────┐
  │  PackageManagerService                   [AOSP, UNMODIFIED]  │
  │  APK signature scheme v2/v3/v3.1 · v3 rotation proof ·       │
  │  update signature continuity · UID assignment · SELinux      │
  │  label assignment · permission grant                         │
  └──────────────────────────────────────────────────────────────┘
```

**Invariants** — stated as invariants because they will come under pressure:

1. Exactly **one** install path: `PackageInstaller` → `PackageManagerService`.
2. Source trust changes **what the user is told**, never **what the system
   permits**. A fully trusted source and a completely unknown one produce a
   bit-identical install operation.
3. Signature verification is never conditional on source — including the
   FreeDroid Store.
4. No application holds `INSTALL_PACKAGES` on a production build.

**On scanning:** FreeDroid may surface signals — signature novelty, requested
permission breadth, reproducible-build attestation where a repository publishes
it. None establishes that an application is safe, and the UI must not imply
otherwise. The honest claim is "nothing known-bad found."

### 4.6 OTA infrastructure — Phase 8

```text
  FreeDroid build  →  offline/HSM signing  →  update server
                                                    │
                                              HTTPS (transport only —
                                              confers no authority)
                                                    ▼
  ┌──────────────────────────────────────────────────────────────┐
  │  DEVICE                                                      │
  │  1. Signature verified against a public key baked into the   │
  │     read-only, Verified-Boot-protected system image          │
  │  2. Rollback index checked — downgrade refused               │
  │  3. Applied to the inactive slot (Virtual A/B)               │
  │  4. dm-verity verifies the new slot at boot                  │
  │  5. Failure → automatic fallback to the previous slot        │
  └──────────────────────────────────────────────────────────────┘
```

Design commitments:

- **Transport is never trust.** An update from the official URL with a bad
  signature is rejected exactly as one from anywhere else.
- Signing keys never enter the repository. Release signing happens on an HSM or
  air-gapped host with multi-party authorization.
- Security-only updates ship independently of feature releases.
- **APEX/Mainline modules are delivered by FreeDroid itself.** Without Google
  Play, a device receives no Play system updates, and a significant share of
  monthly security fixes lands in APEX modules. This is a hard requirement of the
  update system — a Google-free device that skips it silently falls behind on
  fixes it appears to have.

---

## 5. Phone and tablet: one codebase

### 5.1 Strategy

**One codebase. One build per architecture. One set of applications.**

Form factor is a runtime property, not a build-time identity. Two codebases
diverge, and divergence means a security fix applied to one and forgotten on the
other — a security failure, not merely a maintenance cost.

| Mechanism | Purpose |
| --- | --- |
| Resource qualifiers (`sw600dp`, `w840dp`, `-land`, `-port`) | Layout and resource selection |
| `WindowSizeClass` (COMPACT / MEDIUM / EXPANDED) | Runtime layout decisions |
| `WindowMetricsCalculator` | Current **window** bounds — not display bounds |
| Activity embedding (`SplitController`) | Two-pane list/detail on large windows |
| Multi-window, split-screen, freeform | Supported, not special-cased |
| `PRODUCT_CHARACTERISTICS` | Only where hardware genuinely differs |

### 5.2 Phone architecture

| Aspect | Approach |
| --- | --- |
| Window class | Typically COMPACT; MEDIUM in landscape on larger phones |
| Navigation | Bottom navigation or gesture navigation |
| Layout | Single pane; detail views as full-screen destinations |
| Hardware | Telephony, SMS, cellular data, GPS, NFC typically present |
| Foldables | Window class changes at runtime across the hinge — state must survive |

### 5.3 Tablet architecture

| Aspect | Approach |
| --- | --- |
| Window class | Typically EXPANDED; MEDIUM in split-screen |
| Navigation | Navigation rail or drawer |
| Layout | Two-pane list/detail via activity embedding; three-pane where useful |
| Hardware | Telephony often **absent** — features must degrade cleanly, not break |
| External displays | Multi-display and freeform windows supported |

### 5.4 The rule

**Prohibited in FreeDroid UI code:**

```kotlin
if (isTablet()) { … }                                       // device category
if (resources.configuration.smallestScreenWidthDp >= 600)   // device proxy
if (display.width > 1200) { … }                             // display, not window
```

**Required:**

```kotlin
when (WindowSizeClass.compute(windowMetrics).windowWidthSizeClass) {
    WindowWidthSizeClass.COMPACT  -> singlePane()
    WindowWidthSizeClass.MEDIUM,
    WindowWidthSizeClass.EXPANDED -> twoPane()
}
```

The window is not the display. A tablet in split-screen hands an app a compact
window; a foldable changes window size mid-session. Branching on device category
is wrong in both cases — a correctness requirement, not a style preference.

Android 16 additionally ignores orientation and resizability restrictions on
large screens for apps targeting SDK 36. FreeDroid's applications must handle
arbitrary window sizes, and FreeDroid must not reintroduce restrictions that AOSP
removed — doing so would make FreeDroid *less* compatible than stock.

---

## 6. Google Play and GMS

> **AOSP does not include the Google Play Store or Google Mobile Services.**
> They are proprietary, separately licensed, and not part of the open-source
> platform. FreeDroid does not add them, and does not bundle unofficial
> reimplementations or redistributions of them.

FreeDroid defines two conceptual configurations:

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
                                         compliance and a Google licensing
                                         and certification agreement.
                                         NOT GUARANTEED.
```

### FreeDroid Open — the default

The default configuration and the one that must always work. Applications
install, update, and run; notifications, location, and networking function
without any Google component.

### FreeDroid GMS — aspirational

A configuration where Google components may be present **only** where licensing,
certification, and technical requirements permit.

**Explicit constraints:**

- Google components are **not** included in this repository.
- Proprietary Google components are **not** copied or redistributed.
- Unofficial Google packages and reimplementations are **not** bundled.
- **GMS approval is not guaranteed.** It requires a commercial agreement with
  Google, on terms Google sets, and may never be obtained. Nothing in this
  project should be planned on the assumption that it will be.

### Consequences for FreeDroid Open

Applications depending on proprietary Google APIs will degrade or fail:

| Dependency | Effect |
| --- | --- |
| FCM push | No push notifications — often a *silent* failure, which is worse than a loud one |
| Play Billing | In-app purchases fail |
| Play Integrity / SafetyNet | Attestation fails; some banking and media apps refuse to run |
| Maps SDK | Map surfaces fail to load |
| Play Services location | Falls back to AOSP `LocationManager` where the app supports it |

Obligations this creates: the Store **labels** such applications before
installation, and FreeDroid does not claim they work. Discovering it after
installation is a user-hostile failure.

Also: no Play means no Play system updates, which makes FreeDroid-delivered APEX
updates mandatory (§4.6).

### Future milestone

```text
Android compatibility → CTS/CDD compliance → GMS licensing/certification process
```

Sequential, and each step is a prerequisite for the next. FreeDroid is at step
zero: compatibility is a design commitment that has not been tested. CTS/CDD
compliance is a Phase 9–12 objective. GMS licensing is a commercial process that
follows demonstrated compliance and may not be available to this project at all.

---

## 7. Third-party applications

FreeDroid is intentionally designed to allow:

```text
FreeDroid Store          first-party, verified applications and updates
Third-party stores       F-Droid-style repositories and other stores
Direct APK installation  user-approved, from a file or a browser
```

**All applications, from all sources, remain subject to:**

- Android package management (`PackageInstaller` → `PackageManagerService`)
- Package and signature validation (v2/v3/v3.1, v3 rotation, update continuity)
- The Android permission model
- The application sandbox (per-app UID, private data directory)
- SELinux (`untrusted_app` domain)
- Every other platform security control

**Direct APK installation bypasses none of these.** "Direct" describes where the
file came from, not how it is installed.

**No privileged universal APK installation API will be created.** No FreeDroid
interface allows an application to install packages without going through
`PackageInstaller` and its user confirmation. Such an API would be the single
most valuable target on the device — every malicious application would seek it —
and it would make the sandbox negotiable.

---

## 8. What FreeDroid deliberately does not do

- **No root for applications.** No `su`, no root helper, no developer toggle
  granting it, in any build variant.
- **No universal privileged API.**
- **No permission model replacement.** Android's runtime permissions, one-time
  grants, hibernation, Privacy Dashboard, camera/mic indicators and global
  toggles, and Photo Picker already implement what is needed. A parallel
  permission system would be a second enforcement point that can disagree with
  the first — a vulnerability class, not a feature.
- **No modified ART or bytecode contract.**
- **No writable system partitions.** `/system`, `/product`, `/vendor` read-only
  and dm-verity protected.
- **No attestation spoofing**, even where it would improve app compatibility.

---

## 9. Open questions

Recorded rather than resolved; resolving them now would be guessing.

1. **Update cadence.** Monthly SPL merges are the intent. Achievable cadence is
   unknown until merge and test cost are measured on a real baseline.
2. **Reproducible builds.** Highly desirable for supply-chain assurance. AOSP is
   not reproducible out of the box. Scope after Phase 1.
3. **Repository index format.** Consume the F-Droid index directly, or define a
   FreeDroid index with a compatibility shim? Decide in Phase 7 with real
   repositories in hand.
4. **APEX delivery** without Play system updates — staged installs or
   OTA-bundled. Decide in Phase 8.
5. **Reference hardware.** Not selected. Mandatory criterion: a bootloader that
   can be **re-locked with a custom AVB key**. See
   [`DEVICE_SUPPORT.md`](../development/DEVICE_SUPPORT.md) §6.
6. **FreeDroid GMS** — whether to pursue it at all, given the commercial and
   philosophical cost.
