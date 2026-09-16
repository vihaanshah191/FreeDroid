# FreeDroid — Architecture Overview

**Status:** Design intent. Nothing in this document has been built or measured yet.
**Baseline:** AOSP `android-16.0.0_r4`

---

## 1. What FreeDroid is

FreeDroid is an Android-derived operating system. It is **not** a new application
runtime, and it is not a compatibility layer that happens to run Android apps.
An ordinary Android APK, built against the public SDK, runs on FreeDroid through
exactly the same path it runs on any other Android device:

```text
Android APK (unmodified)
        ↓
PackageManager  ──  signature verification, permissions, sandbox assignment
        ↓
Android Framework  ──  Activity/Window/Notification managers, Binder IPC
        ↓
ART  ──  unmodified runtime, unmodified bytecode contract
        ↓
Native layer  ──  Bionic, unmodified NDK ABI
        ↓
Linux kernel  ──  SELinux, seccomp, UID isolation
        ↓
Hardware
```

FreeDroid adds a layer beside that path, not inside it. The distinction matters:
anything placed *inside* that path is a compatibility risk and a security risk,
and must be justified under
[`ANDROID_COMPATIBILITY.md`](../compatibility/ANDROID_COMPATIBILITY.md).

## 2. Layering

```text
┌─────────────────────────────────────────────────────────────┐
│  Applications                                               │
│  Third-party APKs · FreeDroid first-party apps              │
│  (identical API surface — no privileged app class)          │
├─────────────────────────────────────────────────────────────┤
│  FreeDroid UI layer                                         │
│  Launcher · SystemUI extensions · Settings injection        │
│  One adaptive codebase — phone and tablet by resource       │
│  qualifiers and window size classes, not by separate builds │
├─────────────────────────────────────────────────────────────┤
│  FreeDroid framework extensions                             │
│  SourceTrustService · UpdateService · FreeDroid SDK         │
│  Additive. Ordinary apps reach these only through normal    │
│  permission-guarded Binder interfaces.                      │
├─────────────────────────────────────────────────────────────┤
│  Android Framework                     ← UNMODIFIED         │
│  PackageManager · ActivityManager · WindowManager ·         │
│  PermissionController · Binder · Intents · lifecycle        │
├─────────────────────────────────────────────────────────────┤
│  ART                                   ← UNMODIFIED         │
├─────────────────────────────────────────────────────────────┤
│  Native / HAL                          ← UNMODIFIED         │
│  Bionic · NDK ABI · HIDL/AIDL HALs · Keystore/KeyMint       │
├─────────────────────────────────────────────────────────────┤
│  Linux kernel                                               │
│  SELinux enforcing · seccomp · UID/GID isolation · dm-verity│
├─────────────────────────────────────────────────────────────┤
│  Verified Boot (AVB 2.0) · bootloader · hardware root       │
└─────────────────────────────────────────────────────────────┘
```

The two layers marked **UNMODIFIED** are where compatibility lives. Changes there
are not forbidden, but they are the expensive kind, and they carry the review gate.

## 3. Components FreeDroid adds

| Component | Type | Phase | Purpose |
| --- | --- | --- | --- |
| **FreeDroidLauncher** | System app | 4 | Home screen. Adaptive phone/tablet layout. Replaces Launcher3 via `PRODUCT_PACKAGES`. |
| **SystemUI overlays** | RRO + additive | 5 | Branding, quick settings tiles, status bar treatment. Overlays where possible; SystemUI source patches only where a tile or component cannot be injected. |
| **Settings injection** | Settings injection API | 5 | FreeDroid settings appear inside AOSP Settings via the standard injection mechanism, not a forked Settings app. |
| **FreeDroid Store** | Ordinary app + installer role | 7 | First-party app source. Holds `REQUEST_INSTALL_PACKAGES`; is **not** privileged beyond that. |
| **SourceTrustService** | System service | 7 | Records per-source user trust decisions; supplies the installer UI with source provenance. Does **not** install anything itself. |
| **FreeDroid Updater** | System app + `update_engine` | 8 | Fetches, verifies, and stages signed OTAs via AOSP `update_engine`. |
| **UpdateService** | System service | 8 | Update policy, staging, SPL reporting. |
| **Branding / RROs** | Resource overlays | 4 | Boot animation, wallpapers, icons, colors, strings. Zero source changes. |
| **`vendor/freedroid/sepolicy`** | SELinux policy | 6+ | Additive domains for the above. Never `permissive`. |

## 4. The application distribution architecture

This is the part of FreeDroid that is genuinely novel, so it is the part most
likely to be got wrong. The design rule is: **add provenance and user control
around Android's installer, never a second installer path.**

```text
  FreeDroid Store          Third-party store         Direct APK
  (first-party)            (F-Droid, other)          (file, browser, adb sideload)
        │                          │                        │
        │  each holds REQUEST_INSTALL_PACKAGES, or is the
        │  user-selected default installer — nothing more
        ↓                          ↓                        ↓
  ┌───────────────────────────────────────────────────────────────┐
  │  SourceTrustService                                           │
  │  Records: which sources the user has trusted, when, and with  │
  │  what scope. Supplies provenance to the confirmation UI.      │
  │  Advisory only — it cannot grant install capability.          │
  └───────────────────────────────────────────────────────────────┘
        ↓                          ↓                        ↓
  ┌───────────────────────────────────────────────────────────────┐
  │  android.content.pm.PackageInstaller      ← AOSP, UNMODIFIED  │
  │  Session-based install. setPackageSource() carries provenance.│
  │  User confirmation UI. No bypass path exists, for anyone.     │
  └───────────────────────────────────────────────────────────────┘
        ↓
  ┌───────────────────────────────────────────────────────────────┐
  │  PackageManagerService                    ← AOSP, UNMODIFIED  │
  │  APK signature scheme v2/v3/v3.1 verification · v3 rotation   │
  │  proof-of-rotation · update signature continuity · UID        │
  │  assignment · SELinux label assignment · permission grant     │
  └───────────────────────────────────────────────────────────────┘
```

**Invariants, stated as invariants because they will be under pressure:**

1. There is exactly **one** install path: `PackageInstaller` → `PackageManagerService`.
   FreeDroid adds no second path and no "trusted source" fast path.
2. Being a *trusted source* in FreeDroid's UI affects **what the user is told**,
   not **what the system permits**. A fully trusted source and a completely
   unknown one produce a bit-identical install operation. Trust changes warning
   copy and friction, nothing else.
3. Signature verification is never conditional on source. There is no source,
   including the FreeDroid Store, for which validation is relaxed.
4. No app — including FreeDroid's own Store — receives `INSTALL_PACKAGES`
   (the privileged, no-confirmation permission) on a production build. First-party
   apps use `REQUEST_INSTALL_PACKAGES` like everyone else. If FreeDroid's own
   store cannot live within the model, the model is not real.

**On scanning:** FreeDroid may surface signals — signature novelty, requested
permission set, source reputation, reproducible-build attestation where a
repository provides it. None of these establish that an application is safe, and
the UI must not imply that they do. Malware detection by static inspection is
undecidable in general and routinely evaded in practice. The honest claim is
"we found nothing known-bad," and that is the claim the UI will make.

## 5. Phone and tablet: one codebase

There is one build, one set of apps, and one system image per architecture.
Form-factor differences are expressed through Android's existing mechanisms:

| Mechanism | Used for |
| --- | --- |
| Resource qualifiers (`sw600dp`, `w840dp`, `-land`, `-port`) | Layout selection |
| `WindowSizeClass` / `WindowMetricsCalculator` | Runtime layout decisions — **not** `isTablet()` style screen-size guesses |
| Activity embedding (`SplitController`) | Two-pane list/detail on large screens |
| Multi-window, split-screen, freeform | Supported, not special-cased |
| `PRODUCT_CHARACTERISTICS` | Only where hardware truly differs (e.g. telephony present/absent) |

Hard rule: **no `if (tablet)` branching in FreeDroid UI code.** A device is a set
of window metrics and hardware features, not a category. Foldables — which change
category at runtime — make this a correctness requirement rather than a style
preference. Android 16 additionally ignores orientation and resizability
restrictions on large screens for apps targeting SDK 36, so layouts must handle
arbitrary window sizes regardless.

## 6. Google services

AOSP contains no Google Play Store and no Google Play Services. FreeDroid does not
add them.

- **Default configuration is Google-free** and must be fully functional: apps
  install, update, and run; notifications, location, and networking work.
- Apps depending on proprietary Google APIs (FCM push, Play Billing, Play
  Integrity, Maps SDK, Play Services location) will degrade or fail. This is a
  property of those apps, not a FreeDroid defect. The Store must **label such
  apps clearly** rather than let users discover it after installation.
- A separately-maintained configuration may support Google services where
  licensing, certification, and technical requirements permit. It is not the
  default and is not required for any FreeDroid function.
- No proprietary Google component is copied or redistributed from this project.

**Consequence that is easy to miss:** without Google Play, FreeDroid does not
receive Mainline (APEX) module updates through Play system updates. Those modules
carry a significant share of monthly security fixes. FreeDroid must therefore
deliver APEX updates itself — either as staged APEX installs or rolled into OTAs.
This is a hard requirement of the update system, not an optimization. See
[`SECURITY_MODEL.md`](../security/SECURITY_MODEL.md) §T4 and the OTA phase in the
roadmap.

## 7. Build variants

| Variant | `ro.debuggable` | ADB default | SELinux | Verified Boot | Signing key |
| --- | --- | --- | --- | --- | --- |
| `eng` | 1 | on | **enforcing** | test keys | test |
| `userdebug` | 1 | on, authorized | **enforcing** | test keys | test |
| `user` | **0** | **off** | **enforcing** | **release AVB** | **release (offline/HSM)** |

Two properties hold across every variant, including `eng`:

- SELinux is enforcing. There is no permissive FreeDroid build.
- Package signature verification is enabled. There is no build in which it is not.

Everything else that differs between variants is gated by an automated release
check that fails the build if a debug setting appears in a `user` image. The gate
exists because "we'll remember to turn it off" is how debug settings ship. See
[`TESTING.md`](../development/TESTING.md) §4.

## 8. What FreeDroid deliberately does not do

- **No root access for applications.** No `su`, no root helper, no "developer
  mode" that grants it.
- **No universal privileged API.** There is no FreeDroid interface through which
  an ordinary app performs a privileged operation on its own behalf. Each
  FreeDroid service exposes a narrow, permission-guarded interface or none.
- **No permission model replacement.** Android's runtime permissions, one-time
  grants, unused-app hibernation, Privacy Dashboard, camera/mic indicators and
  global toggles, and Photo Picker already implement what requirement
  "application security" asks for. FreeDroid surfaces and defaults these better;
  it does not reimplement them. A parallel permission system would be a second
  enforcement point that can disagree with the first — which is a vulnerability
  class, not a feature.
- **No modified ART or bytecode contract.**
- **No system partition writable at runtime.** `/system`, `/product`, `/vendor`
  are read-only and dm-verity protected.

## 9. Open questions

Recorded rather than resolved, because resolving them now would be guessing:

1. **Update cadence.** Monthly SPL merges from `android16-security-release` are
   the intent. Achievable cadence is unknown until we measure merge and test cost
   on a real baseline.
2. **Reproducible builds.** Highly desirable for supply-chain assurance
   (`SECURITY_MODEL.md` §T10). AOSP is not fully reproducible out of the box.
   Scope to be assessed after Phase 1.
3. **Repository format for third-party sources.** Whether to consume the F-Droid
   index format directly or define a FreeDroid index with a compatibility shim.
   Decide in Phase 7 with real repositories in hand.
4. **APEX update delivery** without Play system updates — staged APEX installs
   versus OTA-bundled. Decide in Phase 8.
5. **Reference hardware.** Phase 10. Selection criteria: unlockable *and
   re-lockable* bootloader with custom AVB key support, mainline-ish kernel,
   available vendor blobs with redistribution rights. Re-lockability is
   non-negotiable — without it, Verified Boot on the shipped device is theatre.
