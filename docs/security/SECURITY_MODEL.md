# FreeDroid — Security Model

**Phase:** 0 (Environment + Architecture)
**Status:** Design intent.
**Planned baseline:** AOSP `android-16.0.0_r4` — *planned, not synced*

---

## 0. How to read this document

> **NOTHING IN THIS DOCUMENT IS IMPLEMENTED.**
>
> No FreeDroid build exists. No image has booted. No security mechanism has been
> tested. Every statement about FreeDroid below describes **intent**.

Each mechanism in Part I is split four ways, because these are different kinds of
claim and conflating them is how security documentation becomes dishonest:

| Field | Meaning |
| --- | --- |
| **AOSP provides** | Already exists upstream. FreeDroid inherits it by not breaking it. |
| **FreeDroid intends** | What FreeDroid plans to add, configure, or enforce. **Not built.** |
| **Requires hardware** | What the property depends on from the device. Absent hardware, the property does not exist regardless of software. |
| **Verification** | The test that would demonstrate it, and its current status. |

**The governing rule:**

> A configuration file containing the expected value is not evidence that a
> security mechanism works. `BOARD_SELINUX_ENFORCING := true` proves someone
> wrote a line in a makefile. `getenforce` returning `Enforcing` on a booted
> image proves SELinux is enforcing. Only the second is evidence.

### Security principles

1. **Extend Android's security model; never replace it.** A parallel enforcement
   mechanism is a second authority that can disagree with the first. Disagreement
   between two authorities on the same question is a vulnerability class.
2. **Freedom of installation is not freedom from the sandbox.** Users choose
   sources; the sandbox applies identically regardless of source.
3. **No security decision depends on where an application came from.**
4. **Never trade security for convenience.** Not for development speed, not for
   app compatibility, not for a smoother install flow.
5. **State limitations honestly.** FreeDroid will not claim to detect malware it
   cannot detect, or to protect against attacks it cannot stop.

### Trust boundaries

```text
┌──────────────────────────────────────────────────────────────┐
│ UNTRUSTED   third-party apps · app stores · repositories ·   │
│             downloaded APKs · USB hosts · network peers      │
├──────────────────────────────────────────────────────────────┤
│ SEMI-       FreeDroid Store · FreeDroid first-party apps     │
│ TRUSTED     Same sandbox. Same permissions. No exemptions.   │
├──────────────────────────────────────────────────────────────┤
│ TRUSTED     Android framework · system services · HALs       │
├──────────────────────────────────────────────────────────────┤
│ ROOT OF     Verified Boot chain · hardware keystore (TEE /   │
│ TRUST       StrongBox) · bootloader · release signing keys   │
└──────────────────────────────────────────────────────────────┘
```

The FreeDroid Store sitting in SEMI-TRUSTED rather than TRUSTED is the
load-bearing decision of this model. If our own store needed privileges a
third-party store cannot have, the multi-source promise would be decorative.

---

# PART I — SECURITY MECHANISMS

## 1. Application sandboxing

**AOSP provides.** A unique UID per application; a private data directory
unreadable by other apps under DAC; a per-app SELinux context (`untrusted_app`)
that constrains even code running as that UID; scoped storage with no arbitrary
filesystem access; seccomp-bpf syscall filtering; `isolatedProcess` and a
separate WebView renderer sandbox; W^X enforcement blocking common code-injection
paths. This is kernel-enforced, not framework-enforced.

**FreeDroid intends.** To **not break it**, and to verify that it holds
identically for applications from every source. FreeDroid adds no sandbox
exemption, no privileged application class, and no mechanism by which a trusted
source produces a less-confined application. Additive SELinux policy for
FreeDroid services must never weaken an existing AOSP domain.

**Requires hardware.** Nothing for the base sandbox — UID isolation and SELinux
are kernel features. Memory-safety hardening that *reinforces* it (MTE, PAC, BTI)
is SoC-dependent and absent on Cuttlefish x86_64.

**Verification.** SEC-03 (cross-app data access denied by DAC **and** SELinux —
both, since either alone is not the property). ⬜ **Not tested.**

## 2. SELinux enforcing

**AOSP provides.** A complete mandatory access control policy covering every
system domain, `neverallow` assertions that fail the policy build when violated,
and CTS/VTS tests asserting them at runtime.

**FreeDroid intends.** SELinux **enforcing in every build variant, including
`eng`** — there is no permissive FreeDroid build. Additive policy only, in
`vendor/freedroid/sepolicy/`, with a dedicated minimal domain per FreeDroid
service. No AOSP domain weakened. No `permissive` domain in any variant. Target:
zero unexpected denials in normal operation.

**Requires hardware.** Nothing. SELinux is a kernel feature available on all
targets including Cuttlefish.

**Verification.** SEC-01 (`getenforce` → `Enforcing` on a booted image, every
variant), SEC-15 (no unexplained denials). ⬜ **Not tested.**

## 3. Verified Boot

**AOSP provides.** A boot-time integrity chain rooted in the bootloader, with
boot states GREEN (locked, OEM key) / YELLOW (locked, user key) / ORANGE
(unlocked) / RED (verification failed), and a mandatory warning screen for
YELLOW and ORANGE.

**FreeDroid intends.** Verified Boot enabled in production, never disabled to
simplify development. Devices supported only where the bootloader can be
**re-locked with a custom AVB key** — without re-locking the chain is not rooted
in our key and the property does not exist. Unlocking forces a factory reset, so
it cannot be used to reach existing user data. Development uses AOSP test keys,
which are publicly known and provide **no** authenticity guarantee.

**Requires hardware.** A bootloader implementing AVB; tamper-evident storage for
the root key; the ability to re-lock with a custom key. **Most commercial devices
fail the last requirement.** Cuttlefish exercises the chain logically but gives
no hardware root of trust.

**Verification.** SEC-06 (modified image fails to boot / does not reach GREEN).
⬜ **Not tested.**

## 4. AVB — Android Verified Boot 2.0

**AOSP provides.** `vbmeta` chaining to per-partition hash and hashtree
descriptors; dm-verity verifying `/system`, `/product`, `/vendor` **block-by-block
at read time**, not once at boot; the `avbtool` signing toolchain.

**FreeDroid intends.** A full AVB chain over all verified partitions, signed with
a FreeDroid release key held offline or in an HSM. dm-verity enabled on
production builds. Read-only mounts with no runtime write path for any
application. `adb remount` unavailable on `user` builds.

**Requires hardware.** Bootloader AVB support; a tamper-evident location for the
root key hash. Without a locked bootloader, AVB verifies nothing an attacker
cannot replace.

**Verification.** SEC-06, SEC-11 (system partitions not writable). ⬜ **Not tested.**

## 5. Rollback protection

**AOSP provides.** Monotonic rollback indexes in AVB metadata; bootloader refusal
of images whose index is below the stored value; `RollbackManager` for APK/APEX
rollback (a distinct mechanism from AVB rollback).

**FreeDroid intends.** A monotonically increasing rollback index per release,
incremented whenever a release fixes a vulnerability that a downgrade would
reopen. Rollback protection enabled in production, validated on real hardware
before any such claim is made.

**Requires hardware.** **Tamper-evident, monotonic storage** (RPMB or
equivalent). This is a hard hardware dependency. A device without it cannot
enforce rollback protection at all, no matter what software does. Cuttlefish does
not provide it, and an unlocked bootloader defeats it regardless.

**Verification.** SEC-08 (downgrade attempt refused — on real hardware; an
emulator result would not demonstrate this). ⬜ **Not tested.**

## 6. Encryption

**AOSP provides.** File-Based Encryption with Credential Encrypted (CE) storage
inaccessible until first unlock and Device Encrypted (DE) storage for what must
work before it; metadata encryption for everything outside FBE scope; keys
derived from a hardware secret combined with the user credential; hardware
rate-limited key derivation (Gatekeeper/Weaver); the Before-First-Unlock state in
which user data keys are not resident in memory.

**FreeDroid intends.** FBE enabled on all targets, using **Android's supported
encryption mechanisms** — no custom cryptography. Lock screen requirement
communicated plainly during setup, since without a credential there is no
meaningful encryption. Under evaluation for Phase 9: an inactivity-triggered
reboot returning the device to the BFU state, following published prior art.

**Requires hardware.** A hardware-backed keystore (TEE minimum) for key
derivation and rate limiting. Without it, encryption degrades to software-only
and brute-force resistance largely disappears. Hardware-wrapped keys and inline
crypto engines are further device-dependent.

**Verification.** SEC-12 (userdata encrypted; CE inaccessible before first
unlock). ⬜ **Not tested.**

## 7. Hardware-backed Keystore

**AOSP provides.** The Keystore/KeyMint HAL; key generation and use inside the
TEE or StrongBox with keys never exposed to the OS; key attestation proving
hardware backing, boot state, and patch level; auth-bound keys requiring user
authentication; StrongBox as a discrete secure element where present.

**FreeDroid intends.** To use hardware-backed keys where available and to
**report device state honestly through attestation**, including when that state
causes applications to refuse to run. FreeDroid will not spoof attestation:
spoofing would undermine the same integrity guarantees §12 depends on, and would
lie to the relying party.

**Requires hardware.** A TEE for basic hardware backing; StrongBox for the
stronger guarantee; a vendor attestation key provisioned at manufacture.
**Cuttlefish provides a software-emulated KeyMint with no hardware guarantee
whatsoever.** Any Keystore result from Phase 1–9 testing demonstrates API
behavior, not hardware protection.

**Verification.** SEC-13 (hardware-backed key not extractable; attestation
reports hardware backing) — meaningful only on physical hardware, Phase 10.
⬜ **Not tested.**

## 8. OTA signing

**AOSP provides.** `ota_from_target_files` package generation; `update_engine`
payload signature verification against a key in the read-only system image;
Virtual A/B slot-based updates with automatic fallback; post-install dm-verity
verification; staged application at reboot rather than live patching.

**FreeDroid intends.** Every OTA signed with a release key held in an HSM or on
an air-gapped host, **never in this repository**, with multi-party authorization
and per-operation audit logging including the artifact digest. Verification
against a key baked into the Verified-Boot-protected image, so an attacker must
break signing rather than transport. A security-only channel shipping
independently of feature releases. **FreeDroid-delivered APEX/Mainline updates**,
since a Google-free device receives no Play system updates and a meaningful share
of monthly fixes lands in APEX modules.

**Requires hardware.** A/B partition support for seamless updates; AVB for
post-install verification; tamper-evident storage for rollback indexes. Signing
infrastructure (HSM) is operational, not device, hardware.

**Verification.** SEC-05 (tampered payload, wrong key, and stripped signature all
rejected). ⬜ **Not tested.**

## 9. Signing key management

**AOSP provides.** The `sign_target_files_apks` toolchain and a key-mapping
mechanism. AOSP ships **publicly known test keys**; an image signed with them has
no authenticity guarantee and must never leave a development machine.

**FreeDroid intends.** Release keys in an HSM or on an offline air-gapped host.
Separate keys for platform, release/OTA, and APEX, so compromise of one does not
imply the others. Multi-party authorization for release signing. Audit logging.
A rotation plan **written and rehearsed before it is needed** — APK v3
proof-of-rotation supports app key rotation; AVB key rotation on fielded devices
is materially harder. Test keys named unambiguously and rejected by the release
gate for `user` builds.

**Never, in any form:** production signing keys in Git, hard-coded secrets, keys
in CI environment variables, or keys on the build host.

**Requires hardware.** An HSM for the strongest custody. An air-gapped host is
the minimum acceptable alternative.

**Verification.** Repository secret scanning (implemented in
`scripts/validate-structure.sh`); release gate key check (SEC-16, Phase 9).
⬜ **Not tested** beyond repository scanning.

## 10. Application permissions

**AOSP provides.** Runtime permissions for camera, microphone, location,
contacts, media, notifications, nearby devices, and sensors; one-time grants;
auto-revocation and app hibernation after disuse; the Privacy Dashboard;
camera/microphone indicators and global hardware toggles; the Photo Picker
granting per-item media access with no permission at all; two-step background
location; foreground-service type declarations; restricted settings requiring a
deliberate high-friction grant for accessibility and overlay access on sideloaded
apps; `targetSdk` enforcement blocking legacy permission-model bypasses.

**FreeDroid intends.** To **surface and default these well, not to replace
them**. A permission preview before installation, showing requested permissions
and their sensitivity rather than deferring everything to first use. Clear
revocation paths. **No custom permission framework** — a parallel system would be
a second enforcement point that can disagree with the first, which creates
vulnerabilities rather than closing them. Investigate what Android already offers
before building anything.

**Requires hardware.** Global camera/microphone toggles are most meaningful with
hardware-backed cutoffs, which are device-dependent. Sensor-privacy support
varies.

**Verification.** SEC-14 (protected APIs throw without permission), APP-18/19/20
(grant, denial, revocation). ⬜ **Not tested.**

## 11. Third-party APK installation

**AOSP provides.** Session-based `PackageInstaller` with user confirmation;
`REQUEST_INSTALL_PACKAGES` for non-privileged installers;
`setPackageSource()` carrying provenance; APK signature schemes v2/v3/v3.1 with
v3 key rotation; **update signature continuity** requiring an update to be signed
by the same key as the installed app or a valid rotation chain; UID and SELinux
label assignment at install time.

**FreeDroid intends.** To make direct APK installation a **first-class, supported
path** that bypasses nothing. "Direct" describes where the file came from, not
how it is installed. The confirmation dialog shows source provenance and prior
trust decisions. Honest warnings for unknown sources — warnings that do not claim
an app is safe.

**Explicitly will not be created:** a privileged universal APK installation API.
No FreeDroid interface allows an app to install packages without
`PackageInstaller` and its user confirmation. Such an API would be the most
valuable target on the device and would make the sandbox negotiable.

**Requires hardware.** Nothing.

**Verification.** APP-05 (direct APK installs after confirmation), APP-13
(different-signature update **rejected** — the most important single test),
SEC-09 (verification identical across sources), SEC-10 (signature scheme
enforcement). ⬜ **Not tested.**

## 12. Third-party application stores

**AOSP provides.** The same `PackageInstaller` path for any installer holding
`REQUEST_INSTALL_PACKAGES`; per-installer attribution; update continuity
independent of which installer delivers the update.

**FreeDroid intends.** Multiple concurrent sources — FreeDroid Store,
third-party stores, F-Droid-style repositories, direct APKs — with **none
privileged over another**. Per-source trust records in `SourceTrustService`,
individually revocable, with repository index signing keys pinned on first add
(TOFU) and key changes requiring explicit re-approval. Source provenance shown at
install. Revocation warns about already-installed apps rather than silently
uninstalling the user's software.

**The Store constraint:** the FreeDroid Store holds `REQUEST_INSTALL_PACKAGES`
and **never `INSTALL_PACKAGES`**. No app holds the privileged no-confirmation
permission on a production build. If FreeDroid's own store cannot live within the
model, the model is not real.

**Requires hardware.** Nothing.

**Verification.** SEC-07 (no app holds `INSTALL_PACKAGES`), SEC-09, SEC-17
(`SourceTrustService` cannot be manipulated by apps), APP-03/04/15.
⬜ **Not tested.**

---

# PART II — THREAT ANALYSIS

Each threat states mitigations and — more usefully — **limitations**. A threat
model that reads as though everything is covered is a threat model nobody
checked.

## T1 — Malicious applications

**Threat.** A user installs an application hostile by design: it exfiltrates
data, defrauds, spies, ransoms, or abuses accessibility and overlay APIs.

**This is FreeDroid's highest-likelihood threat**, and supporting arbitrary
sources increases exposure to it. FreeDroid constrains what such an app can
reach; it does not prevent a user from installing one.

**Mitigations.** The sandbox (§1), runtime permissions (§10), scoped storage,
background restrictions, hibernation, privacy indicators, `targetSdk`
enforcement, restricted settings for accessibility and overlay grants, plus
FreeDroid's intended provenance display, pre-install permission preview, and
signal surfacing.

**Limitations.**

- **FreeDroid cannot determine whether an application is malicious.** The general
  problem is undecidable and practical analysis is routinely evaded. Every signal
  is advisory. The UI will say "nothing known-bad found," never "safe."
- **User consent cannot be engineered away.** A user granting accessibility,
  overlay, notification-listener, or device-admin access has handed over most of
  what an attacker needs. Friction helps; it does not solve.
- **Permitted capabilities suffice for real harm.** Network access needs no
  runtime permission; contacts plus network is exfiltration, and that is the
  permission model working as designed.
- Side channels — package enumeration, timing, sensor inference — have no
  complete mitigation.
- Kernel and framework 0-days bypass all of it. A lapsed security patch level is
  a sandbox failure, not a paperwork failure.

## T2 — Compromised applications

**Threat.** A legitimate application is exploited at runtime — malicious input, a
hostile ad SDK, a compromised update — and the attacker runs code with that app's
privileges.

**Mitigations.** UID isolation confines the attacker to that app's UID. SELinux
`untrusted_app` constrains reachable files, sockets, services, and ioctls even
for code running as that UID. seccomp narrows the syscall surface.
`isolatedProcess` and the WebView renderer sandbox contain renderer compromise.
Hardware-backed keys remain non-extractable. The attacker inherits only
already-granted permissions.

**Limitations.**

- Everything the app legitimately holds is forfeit: its data, its permissions,
  its network access, its notification reach.
- **A sandbox escape defeats this layer entirely.** Containment assumes kernel and
  SELinux integrity.
- MTE, PAC, and BTI are SoC-dependent and **absent on Cuttlefish** — Phase 1–9
  testing validates policy, not hardware mitigation.
- Confused-deputy attacks via exported components, implicit intents, and
  `PendingIntent` misuse remain the responsibility of individual apps. FreeDroid
  cannot fix a third-party app's IPC bugs.

## T3 — Privilege escalation

**Threat.** An ordinary application obtains system, root, or kernel privileges.

**This is the threat FreeDroid's design most directly commits to.** Supporting
installation from arbitrary sources is defensible only if an installed app cannot
escape the sandbox.

**Mitigations (intended).** No root for apps in any variant — no `su`, no root
helper, no toggle. No universal privileged API. `signature`-level permissions on
FreeDroid service interfaces, unobtainable by third-party apps. AOSP's privileged
permission allowlist reviewed line by line each release, with unallowlisted
grants failing the build. SELinux `neverallow` assertions. Read-only,
dm-verity-protected system partitions. `INSTALL_PACKAGES` withheld from every
app including the FreeDroid Store. One minimal SELinux domain per FreeDroid
service.

**FreeDroid-specific risks** — the features FreeDroid adds are exactly where this
threat would materialize:

| Risk | Intended control |
| --- | --- |
| Store granted `INSTALL_PACKAGES` "for convenience" | Prohibited; automated release-gate check |
| `SourceTrustService` exposing trust-granting to apps | `signature`-guarded, read-mostly; trust changes only via a Settings UI flow with user interaction |
| `UpdateService` exposing OTA install to apps | No app-reachable install path |
| Framework extensions widening `framework.jar` | FreeDroid SDK ships as a separate library |

**Limitations.**

- **Kernel and TEE vulnerabilities defeat all of it.** Escalation chains are found
  regularly on every mobile platform. Patch cadence is the only mitigation.
- Vendor HAL and driver code is frequently the weakest link and is largely outside
  FreeDroid's control.
- A careless FreeDroid service — ours — could introduce an escalation path.
  Review and a low service count reduce this; they do not eliminate it.
- `userdebug` builds permit root via ADB by design. **They are not production.**

## T4 — Malicious OTA packages

**Threat.** An attacker delivers a crafted system update via DNS hijack,
compromised CDN, hostile network, or compromised update server.

**Mitigations (intended).** Signature verification against a key in the
Verified-Boot-protected system image (§8). **Transport is never trust** — an
update from the official URL with a bad signature is rejected exactly as one from
anywhere else. Rollback index checks. Virtual A/B application to the inactive
slot with automatic fallback. Post-install dm-verity. Signed metadata checked
before download. Staged application at reboot.

**Limitations.**

- **Compromise of the release signing key defeats everything here.** Highest
  consequence risk in the project; controls in §T10.
- Downgrade to an older but validly signed release is prevented only where
  hardware supports rollback indexes (§5).
- A network attacker can **withhold** updates. Signature checks prevent malicious
  updates, not denial of updates. The device should surface its SPL age.
- A malicious but correctly signed update — insider or compromised pipeline —
  passes every on-device check. Only §T10 controls address this.
- Nothing here helps if the bootloader is compromised below the AVB root.

## T5 — Modified system images

**Threat.** An attacker with physical access, or one who achieved root, modifies
the system partition, boot image, or kernel for persistence.

**Mitigations (intended).** The AVB chain (§4) and dm-verity read-time
verification. Rollback protection (§5). Read-only mounts with no runtime write
path. Boot state display with mandatory warnings for YELLOW and ORANGE. Key
attestation exposing boot state to apps. Unlock forces a factory reset.

**Limitations.**

- **An unlocked bootloader disables this entire section.** FreeDroid supports
  user-controlled unlocking as a matter of ownership, and an unlocked device has
  no image-integrity guarantee — the boot warning says so. Re-locking with a user
  AVB key restores a verified chain rooted in *their* key (YELLOW).
- **Verified Boot proves provenance, not absence of vulnerabilities.** A correctly
  signed vulnerable image verifies perfectly.
- Below-the-bootloader compromise (boot ROM, SoC firmware) is out of scope and
  undetectable from above.
- Devices that cannot be re-locked with a custom AVB key cannot run FreeDroid with
  a full verified chain — a hard hardware selection criterion, not a detail to
  discover late.
- dm-verity protects integrity, not confidentiality.

## T6 — Physical attacks

**Threat.** An attacker in physical possession attempts data extraction,
persistent malware installation, or authentication bypass — including forensic
tooling and evil-maid scenarios.

**Mitigations (intended).** FBE with CE storage locked until first unlock (§6).
Metadata encryption. Hardware-bound keys that never leave the TEE.
Hardware-rate-limited brute-force resistance. The BFU state after cold boot.
Verified Boot preventing persistent modification on a locked device. Unlock wipes
data. ADB off by default (§T8). Lockdown mode disabling biometrics on demand.
Under evaluation: inactivity-triggered reboot to BFU.

**Limitations.**

- **After First Unlock is substantially weaker.** Keys are resident in memory and
  commercial forensic tools target this state specifically. A device seized
  powered-on and previously unlocked is at meaningful risk.
- Weak credentials defeat encryption. A 4-digit PIN is 10,000 possibilities;
  hardware rate-limiting makes this slow, not impossible.
- **Biometrics are convenience, not security** — bypassable with varying effort,
  and legally distinct from a passphrase in some jurisdictions.
- Chip-off, fault injection, glitching, side channels, and undisclosed TEE
  vulnerabilities are outside what an OS can mitigate.
- A well-resourced attacker with unlimited physical access and a device-specific
  exploit chain generally wins. FreeDroid raises cost; it does not promise
  immunity.

## T7 — Stolen devices

**Threat.** The device is lost or stolen; the attacker wants data or resale value.

**Mitigations (intended).** Encryption at rest (§6). Lock screen requirement,
stated plainly at setup. Hardware-backed lockouts. AOSP Factory Reset Protection,
requiring no Google account. Lock-screen notification redaction by default.
Lockdown mode.

**Limitations.**

- A device stolen **while unlocked** gives the thief everything currently open.
  Encryption is irrelevant in that state.
- AFU weakness (§T6) applies.
- **No remote wipe or device location by default.** This is a real gap created by
  the Google-free default. AOSP provides Device Policy APIs but FreeDroid ships no
  find-my-device service. Users needing it must adopt a third-party or self-hosted
  option, and FreeDroid should document that rather than imply the need is met.
- FRP is bypassable on some devices via vendor flaws; it deters casual resale, not
  a determined refurbisher.
- Data already synced to cloud services is governed by those services.

## T8 — Malicious USB connections

**Threat.** Juice-jacking, malicious charging stations, forensic extraction docks,
BadUSB peripherals, unauthorized ADB access.

**Mitigations (intended).** **ADB disabled by default on `user` builds**
(`ro.adb.secure=1`, `ro.debuggable=0`), with Developer Options hidden until
deliberately revealed. Host authorization by on-device prompt showing the key
fingerprint, revocable, cleared on factory reset, with "always allow" defaulting
to off. AOSP's block on new USB data connections while locked. Charge-only
default. Wireless ADB requiring a pairing code. No `adb root` or `adb remount` on
`user` builds.

**Limitations.**

- **A user who enables ADB and authorizes a hostile host has granted deep
  access.** No OS setting overrides an explicit user decision.
- USB stack and kernel driver vulnerabilities can be reachable *before* any
  authorization prompt — a recurring CVE class across all mobile platforms.
  Mitigation is patch cadence.
- Malicious HID peripherals can act as a keyboard on an unlocked device.
- `userdebug` builds have ADB and root. **Never distribute them.**
- Charge-only mode does not protect the USB-PD negotiation layer itself.

## T9 — Compromised repositories

**Threat.** A third-party store or repository is compromised, or hostile from the
outset, and serves malicious or backdoored applications.

**This threat is created by FreeDroid's core feature.** Supporting multiple
sources means supporting sources we neither control nor audit.

**Mitigations (intended).** Repository index signing with keys pinned on first
add; key changes requiring explicit re-approval. **Unconditional APK signature
verification** regardless of source. **Update signature continuity** — a
compromised repository cannot push a backdoored update to an app it did not
originally sign; this is the strongest control in this section. Per-source,
individually revocable trust. Source provenance at install. No source-conditional
relaxation. The sandbox applies equally. Reproducible-build attestation surfaced
where a repository publishes it. Revocation warns about installed apps.

**Limitations.**

- **A compromised repository can serve genuinely malicious apps that verify
  perfectly.** Signature verification proves *who signed*, never *what the code
  does*.
- **It can push malicious updates to apps it legitimately signs.** Continuity
  protects apps signed by others, not the repository's own.
- Compromise of an upstream developer's signing key defeats continuity entirely.
- TOFU pinning is only as good as the first fetch; adding a repository over a
  hostile network pins the attacker's key.
- FreeDroid cannot audit third-party repository operational security and will not
  claim to. Listing a repository must not read as endorsement.
- Repositories can serve different content to different clients.
- **Users can be socially engineered into adding hostile repositories** — the most
  likely realization of this threat. UI friction is the only mitigation.

## T10 — Supply-chain attacks

**Threat.** Compromise of source, dependencies, build infrastructure, signing
keys, or release artifacts — producing a malicious OS that passes every on-device
check because it is correctly signed.

**Highest-consequence threat in the project.** Every other mitigation assumes the
OS is what we intended to build.

**Mitigations (intended).**

*Source:* AOSP pinned to a release tag with per-project SHAs recorded via
`repo manifest -r`. The overlay structure keeps the complete FreeDroid delta
small and human-reviewable — a security property, since a delta hidden inside a
vendored AOSP copy cannot be audited by inspection. Mandatory review by someone
other than the author; no direct pushes to release branches; signed commits and
tags; every upstream patch carrying stated impact and a named owner.

*Build:* AOSP prebuilt toolchains, pinned and hash-verified. Release builds only
on controlled infrastructure, never a developer laptop. Build provenance archived
with artifacts. Every non-AOSP dependency pinned by hash, with new dependencies
requiring review — each is another party capable of compromising the OS.

*Keys:* see §9.

*Artifacts:* signed OTA and factory images with published verification
instructions; SHA-256 digests published over a separate channel from the
artifacts; an append-only transparency log so a targeted per-user build is
detectable by comparison (design goal, Phase 8–9).

**Limitations.**

- **A compromised signing key is catastrophic and largely unrecoverable** for
  fielded devices, especially where rollback protection prevents downgrading past
  a maliciously signed release.
- A malicious insider with release authority defeats most controls; multi-party
  authorization raises the bar, collusion defeats it.
- **We depend on upstream AOSP's own supply chain.** A compromise at Google is
  outside our control and largely outside our detection.
- Compiler and toolchain trust ("Reflections on Trusting Trust") is not fully
  solvable. Reproducible builds with independent verification is the best
  available answer and is **not implemented**.
- Build infrastructure compromise produces correctly signed malicious images. Only
  reproducible builds plus independent rebuilds detect this.

---

# PART III — CROSS-CUTTING CONTROLS

## Development / production isolation

The failure this exists to prevent: a debug setting reaching production because
someone intended to revert it and did not.

| Setting | `eng` | `userdebug` | `user` (production) |
| --- | --- | --- | --- |
| `ro.debuggable` | 1 | 1 | **0** |
| ADB | on | on, authorized | **off by default** |
| `adb root` / `remount` | available | available | **unavailable** |
| **SELinux** | **enforcing** | **enforcing** | **enforcing** |
| **Package signature verification** | **on** | **on** | **on** |
| Verified Boot | test keys | test keys | **release keys** |
| dm-verity | may be off | may be off | **on** |
| Signing key | test | test | **release (HSM/offline)** |
| Test/debug apps | present | present | **absent** |

Two properties hold in **every** variant: SELinux enforcing, and package
signature verification on. There is no permissive FreeDroid build and no build
that skips signature validation.

**Enforcement.** `scripts/verify-build-variant.sh` (Phase 9) runs as a mandatory
release gate, failing a `user` build that carries any debug setting, a test-key
signature, a permissive domain, debug apps, or disabled dm-verity. A checklist is
not a control; the gate is the control. Per SEC-16 the gate must itself be tested
against a deliberately bad image — a gate never observed to fail is a gate nobody
has shown to work.

## Prohibited, without exception

```text
❌ disable SELinux                     ❌ hidden or undocumented privileged APIs
❌ disable Verified Boot in production  ❌ production signing keys in Git
❌ root access for applications         ❌ hard-coded secrets or credentials
❌ writable system partitions           ❌ disable package signature validation
❌ bypass Android permission checks     ❌ silently disable security tests
❌ a privileged universal install API   ❌ spoof attestation or device state
```

A failing security test is a **finding**. Silencing it converts a finding into a
latent vulnerability plus a false green build.

---

# PART IV — VERIFICATION STATUS

**Every row is unverified.** This table is the honest state of the project. Rows
move to ✅ only when a test has run and passed on a real build — never when a
feature has merely been implemented.

| # | Claim | Test | Requires | Status |
| --- | --- | --- | --- | --- |
| 1 | Sandbox isolates app data | SEC-03 | Cuttlefish | ⬜ Not tested |
| 2 | SELinux enforcing, all variants | SEC-01 | Cuttlefish | ⬜ Not tested |
| 2 | No unexplained denials | SEC-15 | Cuttlefish | ⬜ Not tested |
| 3 | Verified Boot detects modification | SEC-06 | **Hardware** | ⬜ Not tested |
| 4 | System partitions read-only | SEC-11 | Cuttlefish | ⬜ Not tested |
| 5 | Rollback protection blocks downgrade | SEC-08 | **Hardware (RPMB)** | ⬜ Not tested |
| 6 | FBE; CE locked before first unlock | SEC-12 | **Hardware (TEE)** | ⬜ Not tested |
| 7 | Keystore hardware-backed | SEC-13 | **Hardware (TEE/StrongBox)** | ⬜ Not tested |
| 8 | Invalid OTA rejected | SEC-05 | Cuttlefish | ⬜ Not tested |
| 9 | No keys in repository | `validate-structure.sh` | — | ⬜ Scan only |
| 10 | Permission enforcement | SEC-14 | Cuttlefish | ⬜ Not tested |
| 11 | Different-signature update rejected | **APP-13** | Cuttlefish | ⬜ Not tested |
| 11 | Signature schemes enforced | SEC-10 | Cuttlefish | ⬜ Not tested |
| 12 | No app holds `INSTALL_PACKAGES` | SEC-07 | Cuttlefish | ⬜ Not tested |
| 12 | Verification identical across sources | SEC-09 | Cuttlefish | ⬜ Not tested |
| 12 | `SourceTrustService` not app-manipulable | SEC-17 | Cuttlefish | ⬜ Not tested |
| T3 | No privilege escalation path | SEC-02 | Cuttlefish | ⬜ Not tested |
| T3 | No root available | SEC-18 | Cuttlefish | ⬜ Not tested |
| T8 | ADB off by default on `user` | SEC-04 | Cuttlefish | ⬜ Not tested |
| — | Release gate rejects bad images | SEC-16 | Build host | ⬜ Not implemented |

**Hardware-dependent rows cannot be demonstrated on Cuttlefish.** Emulator
results for rows 3, 5, 6, and 7 would show API behavior, not hardware protection.
Those rows stay ⬜ until Phase 10 on physical hardware.

## Review cadence

- Re-reviewed at each Android major release adoption.
- Re-reviewed whenever a FreeDroid system service or privileged component changes.
- Formal assessment in Phase 9, before any production consideration.
