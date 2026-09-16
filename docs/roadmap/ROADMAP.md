# FreeDroid — Roadmap

**Current phase: 0 (Environment) — complete.**
**Phases 1–3 are blocked on build-host provisioning.**

Phases are not skipped. Each has an entry gate and an exit gate; the exit gate of
one is the entry gate of the next. A phase is complete when its exit criteria are
*demonstrated*, not when its work feels done.

---

## Status

| Phase | Name | Status | Blocker |
| --- | --- | --- | --- |
| 0 | Environment | ✅ Complete | — |
| 1 | AOSP baseline | 🔴 Blocked | Build host: disk, RAM |
| 2 | Emulator | 🔴 Blocked | Build host: `/dev/kvm` |
| 3 | App compatibility | 🔴 Blocked | Phase 2 |
| 4 | FreeDroid identity | ⬜ Not started | Phase 3 |
| 5 | System UI | ⬜ Not started | Phase 4 |
| 6 | FreeDroid framework | ⬜ Not started | Phase 5 |
| 7 | App distribution | ⬜ Not started | Phase 6 |
| 8 | OTA | ⬜ Not started | Phase 7 |
| 9 | Security hardening | ⬜ Not started | Phase 8 |
| 10 | Physical phone | ⬜ Not started | Phase 9 + hardware |
| 11 | Tablet | ⬜ Not started | Phase 10 |
| 12 | Production | ⬜ Not started | Phase 11 |

**Design and documentation work proceeds in parallel** and does not require a
build host. That is what Phase 0's deliverables are.

---

## Phase 0 — Environment ✅

**Done:** Environment audited, hard blockers identified, AOSP baseline strategy
chosen (`android-16.0.0_r4`), repository structure decided (overlay — ADR-0001),
architecture and security documentation written.

**Deliverables:** `ENVIRONMENT_AUDIT.md`, `OVERVIEW.md`, `REPOSITORY_LAYOUT.md`,
`ADR-0001`, `SECURITY_MODEL.md`, `ANDROID_COMPATIBILITY.md`, `BUILD.md`,
`DEVICE_SUPPORT.md`, `TESTING.md`, this roadmap.

**Exit gate:** ✅ met.

---

## Phase 1 — AOSP baseline 🔴

**Entry:** A build host meeting `BUILD.md` §1.

**Work:** `repo init` at `android-16.0.0_r4`; sync; build **unmodified**
`aosp_cf_x86_64_phone-userdebug`; record manifest SHAs and build time; run the
CTS baseline.

**Change nothing.** The purpose is a known-good reference. A baseline with
modifications in it is not a baseline, and every later comparison depends on it.

**Exit gate:**
- [ ] BLD-01 clean build succeeds
- [ ] BLD-04 images generated
- [ ] BLD-07 all three variants build
- [ ] Manifest SHA record archived
- [ ] CTS baseline recorded

---

## Phase 2 — Emulator 🔴

**Entry:** Phase 1 exit. `/dev/kvm` available.

**Work:** Boot the unmodified baseline under Cuttlefish. Capture boot logs.
Establish the baseline security posture by measurement.

**Exit gate:**
- [ ] BLD-05 boots to home screen
- [ ] **SEC-01 `getenforce` → `Enforcing`**
- [ ] SEC-15 no unexplained SELinux denials
- [ ] ADB connects on `userdebug`
- [ ] Boot log archived

---

## Phase 3 — App compatibility 🔴

**Entry:** Phase 2 exit.

**Work:** Verify Android app compatibility on the unmodified baseline — the
reference point against which every FreeDroid change is judged.

**Exit gate:**
- [ ] APP-01…APP-18 pass
- [ ] **APP-11 different-signature update rejected**
- [ ] SEC-03 sandbox isolation verified (DAC **and** SELinux)
- [ ] SEC-14 permission enforcement verified
- [ ] Multi-source installation demonstrated
- [ ] Real-app corpus established and passing

---

## Phase 4 — FreeDroid identity ⬜

**Entry:** Phase 3 exit. Baseline app compatibility proven.

**Work:** Branding via RROs and prebuilts — boot animation, wallpapers, icons,
colors, strings. FreeDroidLauncher, adaptive from the first commit. FreeDroid
product configuration.

**Constraint:** RROs and product config only. No AOSP source modification. If
branding requires a source patch, the branding changes, not the rule.

**Exit gate:**
- [ ] Product builds and boots
- [ ] Launcher functional on COMPACT, MEDIUM, and EXPANDED window classes
- [ ] No `if (isTablet())` in FreeDroid code
- [ ] Phase 3 app tests still pass
- [ ] No CTS regression
- [ ] Zero upstream patches added

---

## Phase 5 — System UI ⬜

**Entry:** Phase 4 exit.

**Work:** SystemUI overlays — notification shade, quick settings, lock screen,
status bar, navigation. Settings injection for FreeDroid settings.

**Constraint:** Overlay and injection first. Each SystemUI source patch is
justified individually under `ANDROID_COMPATIBILITY.md` §5 and counted — a
growing patch count is the early warning that this phase is going wrong.

**Exit gate:**
- [ ] SystemUI functional on both form factors
- [ ] Notifications, QS, lock screen, navigation all working
- [ ] FreeDroid settings reachable via injection
- [ ] No CTS regression
- [ ] Every patch documented with an owner

---

## Phase 6 — FreeDroid framework ⬜

**Entry:** Phase 5 exit.

**Work:** `SourceTrustService`. FreeDroid SDK as a **separate library**, not
merged into `framework.jar`. Additive SELinux policy per service.

**Constraint:** Each service gets its own SELinux domain and a narrow,
permission-guarded interface. No universal privileged API. Each new system
service is a permanent addition to the attack surface and needs to justify
itself on those terms.

**Exit gate:**
- [ ] SEC-02 no privilege escalation path
- [ ] SEC-07 privileged permission audit clean
- [ ] SEC-17 `SourceTrustService` cannot be manipulated by apps
- [ ] SEC-15 no SELinux denials
- [ ] Security review of every new service
- [ ] No CTS regression

---

## Phase 7 — Application distribution ⬜

**Entry:** Phase 6 exit.

**Work:** FreeDroid Store client. Third-party store support. F-Droid-style
repository support. Direct APK installation flow. Per-source trust UI. Install
warnings with honest wording.

**Constraints:**
- One install path: `PackageInstaller` → `PackageManagerService`.
- The Store holds `REQUEST_INSTALL_PACKAGES`, never `INSTALL_PACKAGES`.
- Verification never varies by source.
- No UI text claims an app is safe.

**Exit gate:**
- [ ] SEC-09 verification identical across all sources
- [ ] SEC-07 no app holds `INSTALL_PACKAGES`
- [ ] APP-03/04/05 all sources install correctly
- [ ] APP-12 update continuity holds across sources
- [ ] Source revocation works and warns about already-installed apps
- [ ] Warning copy reviewed for honesty — no safety guarantees expressed or implied
- [ ] Google-dependency labeling implemented

---

## Phase 8 — OTA ⬜

**Entry:** Phase 7 exit.

**Work:** Signed OTA packages. On-device verification. Virtual A/B. Rollback
protection. Failure recovery. Security-only update channel. Staged updates.
**APEX/Mainline delivery** — required because a Google-free device receives no
Play system updates.

**Constraint:** Signing keys never enter the repository. Release signing on an
HSM or offline host, with multi-party authorization.

**Exit gate:**
- [ ] SEC-05 invalid OTAs rejected (tampered, wrong key, unsigned)
- [ ] SEC-08 rollback protection blocks downgrade
- [ ] A/B update applies and activates
- [ ] Induced failure rolls back cleanly
- [ ] Security-only update demonstrated independently of a feature release
- [ ] APEX update path working
- [ ] Key management procedure documented **and rehearsed**, rotation included

---

## Phase 9 — Security hardening ⬜

**Entry:** Phase 8 exit.

**Work:** Formal security review against `SECURITY_MODEL.md`. Execute every SEC
test. Update the verification table with **actual** results. Threat-model
reassessment. Release gate implemented and proven to fail on a bad image.
External review if resources permit.

**Exit gate:**
- [ ] Every SEC test executed and passing
- [ ] Verification table reflects real results, with remaining gaps named
- [ ] SEC-16 release gate demonstrated to fail on an injected debug setting
- [ ] Threat model reviewed and updated
- [ ] Tablet configuration validated on Cuttlefish
- [ ] Every limitation documented honestly

---

## Phase 10 — Physical phone ⬜

**Entry:** Phase 9 exit. Reference device selected per `DEVICE_SUPPORT.md` §5.

**Work:** Device tree, kernel, vendor blobs. AVB with a FreeDroid key.
Re-lock and verify GREEN boot state. Full DEV test suite. CTS Verifier.

**Constraint:** The device must be **re-lockable with a custom AVB key**.
Without it, Verified Boot is theatre and the security model does not hold on
shipped hardware. Verify this before any other porting work.

**Exit gate:**
- [ ] Boots on hardware
- [ ] Locked bootloader, custom key, GREEN boot state
- [ ] SEC-06 Verified Boot detects modification
- [ ] SEC-08 rollback protection on real hardware
- [ ] SEC-12 FBE on a real userdata partition
- [ ] SEC-13 hardware Keystore verified
- [ ] DEV-01…DEV-13 pass
- [ ] CTS + CTS Verifier, no regression
- [ ] Porting checklist complete

---

## Phase 11 — Tablet ⬜

**Entry:** Phase 10 exit.

**Work:** Tablet device configuration. Validate adaptive UI on real large-screen
hardware. Verify graceful degradation where telephony is absent.

**Constraint:** **Same codebase.** No tablet branch, no tablet build of the UI
apps. If a tablet needs a code change that a phone cannot take, the adaptive
design is wrong and gets fixed rather than forked.

**Exit gate:**
- [ ] Boots on tablet hardware
- [ ] All window size classes correct
- [ ] Multi-window, split-screen, external display working
- [ ] Missing hardware features degrade cleanly
- [ ] DEV suite passes
- [ ] No CTS regression
- [ ] Zero form-factor-specific code branches

---

## Phase 12 — Production ⬜

**Entry:** Phase 11 exit. Extensive field testing.

**Requirements before production is even considered:**

- [ ] All SEC tests passing on production hardware
- [ ] External security assessment completed
- [ ] Key management operational, audited, with rehearsed rotation
- [ ] Update infrastructure operational and load-tested
- [ ] Security update cadence demonstrated over multiple cycles — not promised
- [ ] Incident response process defined
- [ ] Vulnerability disclosure policy published
- [ ] CTS/CDD conformance
- [ ] Legal review: licensing, redistribution, export
- [ ] Documentation complete and accurate
- [ ] Support model defined for the device's intended lifetime

**Shipping an OS is a commitment to keep shipping security updates for it.** A
device that stops receiving updates becomes a liability to the person holding it.
That obligation is part of the production decision, not a consideration that
follows it.

---

## Parallel work

Independent of the build-host blocker:

| Track | Work | Phase |
| --- | --- | --- |
| Documentation | Architecture, security, compatibility | 0 ✅ |
| Launcher | Standalone Gradle development and unit tests | 4 prep |
| Store client | UI and repository client, developed as an ordinary app | 7 prep |
| Repository format | F-Droid index compatibility research | 7 prep |
| Hardware research | Reference device evaluation against §5 criteria | 10 prep |
| Build infrastructure | CI design, release gate script | 1 prep |

## Open decisions

1. Build host provisioning — **blocks phases 1–3**
2. Update cadence — measurable only after Phase 1
3. Reproducible builds scope — assess after Phase 1
4. Repository index format — Phase 7
5. APEX delivery mechanism — Phase 8
6. Reference hardware — Phase 10
7. Google-services configuration — whether to support one at all, and on what terms
