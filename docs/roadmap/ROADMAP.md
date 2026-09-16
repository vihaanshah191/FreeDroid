# FreeDroid — Roadmap

**Current phase:** 0 — Environment + Architecture
**Phases 1–3 are blocked** on provisioning an AOSP build host.

Phases are not skipped. Each has prerequisites and exit criteria; one phase's
exit criteria are the next phase's prerequisites. **A phase is complete when its
exit criteria are demonstrated, not when its work feels done.**

---

## Status

| Phase | Name | Status | Blocker |
| --- | --- | --- | --- |
| 0 | Environment + Architecture | 🟡 In progress | — |
| 1 | AOSP Baseline | 🔴 Blocked | Build host: disk, RAM |
| 2 | Cuttlefish Boot | 🔴 Blocked | Build host: `/dev/kvm` |
| 3 | Android App Compatibility | ⬜ Not started | Phase 2 |
| 4 | FreeDroid Branding | ⬜ Not started | Phase 3 |
| 5 | SystemUI | ⬜ Not started | Phase 4 |
| 6 | FreeDroid Framework | ⬜ Not started | Phase 5 |
| 7 | FreeDroid Store + App Distribution | ⬜ Not started | Phase 6 |
| 8 | OTA | ⬜ Not started | Phase 7 |
| 9 | Security Hardening | ⬜ Not started | Phase 8 |
| 10 | Physical Phone | ⬜ Not started | Phase 9 + hardware |
| 11 | Physical Tablet | ⬜ Not started | Phase 10 |
| 12 | Production | ⬜ Not started | Phase 11 |

**Documentation and standalone app development proceed in parallel** and do not
require a build host.

---

## Phase 0 — Environment + Architecture

**Objective.** Establish the development environment assessment, the
architectural foundation, and the repository structure, without downloading AOSP
or modifying any upstream source.

**Prerequisites.** None.

**Deliverables.**
- Environment audit with measured resources and identified blockers
- Repository scaffold (overlay structure, no vendored AOSP)
- `manifests/freedroid.xml` — documented placeholder, no pinned revision
- `docs/architecture/OVERVIEW.md`
- `docs/architecture/REPOSITORY_LAYOUT.md`
- `docs/architecture/decisions/ADR-0001` (overlay structure)
- `docs/security/SECURITY_MODEL.md`
- `docs/compatibility/ANDROID_COMPATIBILITY.md`
- `docs/development/BUILD.md`, `DEVICE_SUPPORT.md`, `TESTING.md`
- `docs/roadmap/ROADMAP.md`
- `scripts/validate-structure.sh`

**Tests.**
- `scripts/validate-structure.sh` passes
- `manifests/freedroid.xml` is well-formed XML
- No AOSP source vendored; no key material committed
- Internal documentation links resolve

**Exit criteria.**
- [x] Environment audited; blockers identified and quantified
- [x] Repository structure decided and recorded as an ADR
- [x] Architecture, security, compatibility, build, device, testing, and roadmap documents exist
- [x] Manifest placeholder present with **no fabricated revisions**
- [x] Structure validation passes
- [ ] Build host provisioning decision made ← **open**

---

## Phase 1 — AOSP Baseline

**Objective.** Obtain and build an **unmodified** AOSP baseline. Establish a
known-good reference and measure build cost.

**Prerequisites.**
- A build host meeting [`BUILD.md`](../development/BUILD.md) §2 — 16+ cores,
  64+ GB RAM, 500+ GB SSD, `/dev/kvm`
- Build dependencies installed; `repo` installed
- Baseline tag re-confirmed (`android-16.0.0_r4` is the current recommendation;
  re-check for a newer tag before pinning)

**Deliverables.**
- Synced AOSP workspace at a pinned release tag
- Built `aosp_cf_x86_64_phone-userdebug` images
- `manifest-<build-id>.xml` recording every project SHA
- Measured sync time, build time, and disk consumption
- CTS baseline results

**Tests.** BLD-01, BLD-04, BLD-07.

**Exit criteria.**
- [ ] Clean build completes with no errors
- [ ] All images generated within partition limits
- [ ] All three variants (`eng`, `userdebug`, `user`) build
- [ ] Manifest SHA record archived
- [ ] CTS baseline recorded
- [ ] **Nothing modified** — the baseline is unmodified AOSP

> **Change nothing in this phase.** A baseline with modifications in it is not a
> baseline, and every later comparison depends on it.

---

## Phase 2 — Cuttlefish Boot

**Objective.** Boot the unmodified baseline under Cuttlefish and establish the
baseline security posture by measurement.

**Prerequisites.** Phase 1 exit criteria. `/dev/kvm` available; user in the `kvm`,
`cvdnetwork`, and `render` groups.

**Deliverables.**
- Booting `aosp_cf_x86_64_phone` instance
- Archived boot logs
- Baseline `getenforce` and SELinux denial audit results
- Documented `launch_cvd` procedure

**Tests.** BLD-05, SEC-01, SEC-15.

**Exit criteria.**
- [ ] Boots to the home screen; boot time recorded
- [ ] **`getenforce` returns `Enforcing`**
- [ ] No unexplained SELinux denials
- [ ] ADB connects on `userdebug`
- [ ] Boot log archived

---

## Phase 3 — Android App Compatibility

**Objective.** Verify Android application compatibility on the unmodified
baseline — the reference point against which every FreeDroid change is judged.

**Prerequisites.** Phase 2 exit criteria. A real-application test corpus
assembled (F-Droid sourced, licensing permitting).

**Deliverables.**
- Test corpus, versioned and documented
- Full APP suite results on unmodified AOSP
- Sandbox and permission enforcement baseline
- Multi-source installation demonstrated

**Tests.** APP-01 … APP-22; SEC-03, SEC-14.

**Exit criteria.**
- [ ] All APP tests pass
- [ ] **APP-13 — different-signature update rejected**
- [ ] SEC-03 sandbox isolation verified by **DAC and SELinux**
- [ ] SEC-14 permission enforcement verified
- [ ] Installation demonstrated from multiple sources
- [ ] Corpus results archived as the compatibility baseline

---

## Phase 4 — FreeDroid Branding

**Objective.** Establish FreeDroid identity and the product configuration using
**overlays and product config only** — no AOSP source modification.

**Prerequisites.** Phase 3 exit criteria — baseline app compatibility proven.

**Deliverables.**
- `vendor/freedroid/` product configuration and RROs
- `device/freedroid/` device configuration for both Cuttlefish targets
- Boot animation, wallpapers, icons, colors, strings
- FreeDroidLauncher — adaptive from the first commit
- `freedroid_cf_x86_64_phone` and `_tablet` products

**Tests.** BLD-01, BLD-05, BLD-08; full APP suite; CTS regression.

**Exit criteria.**
- [ ] Both FreeDroid products build and boot
- [ ] Launcher functional on COMPACT, MEDIUM, and EXPANDED window classes
- [ ] **No `if (isTablet())` or equivalent in FreeDroid code**
- [ ] Phase 3 app tests still pass
- [ ] **No CTS regression**
- [ ] **Zero upstream patches added** — overlays and product config only

---

## Phase 5 — SystemUI

**Objective.** Customize SystemUI and Settings while preserving Android
compatibility.

**Prerequisites.** Phase 4 exit criteria.

**Deliverables.**
- SystemUI overlays: notification shade, quick settings, lock screen, status bar,
  navigation
- FreeDroid settings via the Settings injection mechanism
- Adaptive SystemUI for both form factors
- Any SystemUI patch documented with justification and a named owner

**Tests.** Full APP suite; CTS regression; SEC-15; adaptive layout tests.

**Exit criteria.**
- [ ] SystemUI functional on both form factors
- [ ] Notifications, quick settings, lock screen, navigation all working
- [ ] FreeDroid settings reachable via injection (**not a Settings fork**)
- [ ] No CTS regression
- [ ] Every patch documented with an owner; patch count tracked

> Overlay and injection first. A growing patch count here is the early signal
> that the approach is drifting.

---

## Phase 6 — FreeDroid Framework

**Objective.** Introduce carefully isolated framework extensions and system
services without widening the core framework or creating escalation paths.

**Prerequisites.** Phase 5 exit criteria.

**Deliverables.**
- `SourceTrustService` with a dedicated SELinux domain
- FreeDroid SDK as a **separate library** — not merged into `framework.jar`
- `vendor/freedroid/sepolicy/` additive policy
- Security review record for every new service

**Tests.** SEC-02, SEC-07, SEC-15, SEC-17; full APP suite; CTS regression.

**Exit criteria.**
- [ ] SEC-02 — no privilege escalation path
- [ ] SEC-07 — privileged permission audit clean
- [ ] SEC-17 — `SourceTrustService` not manipulable by applications
- [ ] SEC-15 — no SELinux denials
- [ ] Security review completed for every new service
- [ ] **FreeDroid SDK is a separate library**
- [ ] No CTS regression

---

## Phase 7 — FreeDroid Store + App Distribution

**Objective.** Deliver multi-source application distribution without privileging
any source, including FreeDroid's own.

**Prerequisites.** Phase 6 exit criteria.

**Deliverables.**
- FreeDroid Store client (`REQUEST_INSTALL_PACKAGES` only)
- Third-party store support
- F-Droid-style repository support with index signature verification
- Direct APK installation flow
- Per-source trust UI with revocation
- Install warnings — honest wording, no safety claims
- Google-dependency labelling

**Tests.** APP-03/04/05/15; SEC-07, SEC-09, SEC-10, SEC-17.

**Exit criteria.**
- [ ] SEC-09 — verification identical across all sources
- [ ] SEC-07 — **no application holds `INSTALL_PACKAGES`**
- [ ] All sources install correctly (APP-03/04/05)
- [ ] APP-15 — update continuity holds across sources
- [ ] Source revocation works and warns about already-installed apps
- [ ] **Warning copy reviewed for honesty** — no safety guarantee expressed or implied
- [ ] Google-dependency labelling implemented
- [ ] **No privileged universal install API exists**

---

## Phase 8 — OTA

**Objective.** Implement cryptographically verified, recoverable system updates,
including a security-only channel.

**Prerequisites.** Phase 7 exit criteria. Signing infrastructure available
(HSM or air-gapped host). **No production keys in the repository.**

**Deliverables.**
- Signed OTA package generation
- On-device verification against a key in the Verified-Boot-protected image
- Virtual A/B updates with automatic fallback
- Rollback protection configuration
- Failure recovery
- Security-only update channel
- Staged updates
- **APEX/Mainline delivery** — required without Play system updates
- Key management procedure, documented and rehearsed

**Tests.** SEC-05, SEC-08; OTA apply, fallback, and recovery tests.

**Exit criteria.**
- [ ] SEC-05 — tampered, wrong-key, and unsigned OTAs all rejected
- [ ] A/B update applies and activates
- [ ] Induced failure rolls back cleanly
- [ ] Security-only update demonstrated **independently of a feature release**
- [ ] APEX update path working
- [ ] Key management documented **and rehearsed**, including rotation
- [ ] **No signing key in the repository** (validated automatically)
- [ ] SEC-08 configured — *verification deferred to Phase 10 (needs hardware)*

---

## Phase 9 — Security Hardening

**Objective.** Conduct a formal security review, execute the full SEC suite, and
replace design claims with test results.

**Prerequisites.** Phase 8 exit criteria.

**Deliverables.**
- `scripts/verify-build-variant.sh` — the release gate, implemented
- Full SEC suite executed on Cuttlefish
- `SECURITY_MODEL.md` Part IV updated with **actual** results
- Threat model reassessment
- Tablet configuration validated on Cuttlefish
- External security review, if resources permit

**Tests.** Every SEC test; SEC-16 including the **negative** case; BLD-08.

**Exit criteria.**
- [ ] Every Cuttlefish-testable SEC test executed and passing
- [ ] Verification table reflects real results, with hardware-dependent rows still marked unverified
- [ ] **SEC-16 — release gate demonstrated to FAIL on an injected debug setting**
- [ ] Threat model reviewed and updated
- [ ] Tablet configuration validated
- [ ] Every limitation documented honestly

---

## Phase 10 — Physical Phone

**Objective.** Port FreeDroid to a reference smartphone and validate the
hardware-rooted security properties that Cuttlefish cannot demonstrate.

**Prerequisites.** Phase 9 exit criteria. A device selected against
[`DEVICE_SUPPORT.md`](../development/DEVICE_SUPPORT.md) §6 — **mandatory: the
bootloader must be re-lockable with a custom AVB key.**

**Deliverables.**
- Device tree, kernel, vendor blobs with documented licensing
- AVB configured with a FreeDroid key; device re-locked
- Rollback protection configured
- Full DEV suite results
- CTS and CTS Verifier results
- Completed porting checklist

**Tests.** DEV-01 … DEV-13; **SEC-06, SEC-08, SEC-12, SEC-13** (the
hardware-dependent tests); CTS; CTS Verifier.

**Exit criteria.**
- [ ] Boots on hardware
- [ ] **Locked bootloader, custom AVB key, GREEN boot state**
- [ ] SEC-06 — Verified Boot detects modification
- [ ] SEC-08 — rollback protection blocks downgrade **on real hardware**
- [ ] SEC-12 — FBE verified on a real userdata partition
- [ ] SEC-13 — hardware-backed Keystore verified
- [ ] DEV-01 … DEV-13 pass
- [ ] CTS and CTS Verifier — no regression
- [ ] Porting checklist complete

---

## Phase 11 — Physical Tablet

**Objective.** Add a reference tablet using the **same codebase**, validating the
adaptive architecture on real large-screen hardware.

**Prerequisites.** Phase 10 exit criteria. A tablet selected against the same
mandatory criteria.

**Deliverables.**
- Tablet device configuration
- Adaptive UI validated on real hardware
- Graceful degradation verified where telephony is absent
- DEV suite results for the tablet

**Tests.** DEV-01 … DEV-16; CTS; adaptive layout across all window size classes.

**Exit criteria.**
- [ ] Boots on tablet hardware
- [ ] All window size classes render correctly
- [ ] Multi-window, split-screen, and external display working
- [ ] Missing hardware features degrade cleanly (no broken dialer)
- [ ] DEV suite passes
- [ ] No CTS regression
- [ ] **Zero form-factor-specific code branches**

> **Same codebase.** If a tablet needs a change a phone cannot take, the adaptive
> design is wrong and gets fixed — not forked.

---

## Phase 12 — Production

**Objective.** Determine whether FreeDroid is fit to ship on production hardware.

**Prerequisites.** Phase 11 exit criteria, plus extensive field testing.

**Deliverables.**
- External security assessment report
- Operational key management with audited, rehearsed rotation
- Update infrastructure, load-tested
- Demonstrated security update cadence over multiple cycles
- Incident response process
- Published vulnerability disclosure policy
- CTS/CDD conformance results
- Legal review: licensing, redistribution, export

**Tests.** Full BLD, APP, SEC, DEV, CTS suites on production hardware; sustained
OTA delivery over multiple release cycles.

**Exit criteria.**
- [ ] All SEC tests passing on production hardware
- [ ] External security assessment completed and findings addressed
- [ ] Key management operational and audited
- [ ] Update infrastructure operational and load-tested
- [ ] **Security update cadence demonstrated over multiple cycles — not promised**
- [ ] Incident response process defined
- [ ] Vulnerability disclosure policy published
- [ ] CTS/CDD conformance demonstrated
- [ ] Legal review complete
- [ ] Support model defined for the device's intended lifetime

> **Shipping an OS is a commitment to keep shipping security updates for it.** A
> device that stops receiving updates becomes a liability to the person holding
> it. That obligation is part of the production decision, not a consideration
> that follows it.

---

## Future milestone — GMS

```text
Android compatibility → CTS/CDD compliance → GMS licensing/certification process
```

Sequential; each step is a prerequisite for the next.

| Step | Phase | Status |
| --- | --- | --- |
| Android compatibility | 3, ongoing | Design commitment, untested |
| CTS/CDD compliance | 9–12 | Not started |
| GMS licensing / certification | Post-12 | **Not pursued. Not guaranteed. May never be available.** |

GMS requires a commercial agreement with Google on Google's terms. Nothing in
this project should be planned on the assumption that it will be obtained.

---

## Parallel work

Independent of the build-host blocker:

| Track | Work | Supports phase |
| --- | --- | --- |
| Documentation | Architecture, security, compatibility | 0 |
| Launcher | Standalone Gradle development and unit tests | 4 |
| Store client | UI and repository client as an ordinary app | 7 |
| Repository format | F-Droid index compatibility research | 7 |
| Release gate | `verify-build-variant.sh` logic | 9 |
| Hardware research | Device evaluation against the §6 criteria | 10 |
| Build infrastructure | CI design | 1 |

---

## Open decisions

1. **Build host provisioning** — blocks Phases 1–3
2. Update cadence — measurable only after Phase 1
3. Reproducible builds scope — assess after Phase 1
4. Repository index format — Phase 7
5. APEX delivery mechanism — Phase 8
6. Reference hardware selection — Phase 10
7. Whether to pursue FreeDroid GMS at all
