# FreeDroid — Security Model and Threat Assessment

**Status:** Design intent. **No claim in this document has been empirically verified.**
**Baseline:** AOSP `android-16.0.0_r4`
**Last reviewed:** 2026-09-16

---

## 0. How to read this document

Every mitigation below is currently a **design claim**, not a test result. A
configuration file containing `SELINUX=enforcing` is not evidence that SELinux is
enforcing; `getenforce` returning `Enforcing` on a booted image is. Until the
corresponding test in [`TESTING.md`](../development/TESTING.md) has run and
passed on a real build, treat each item as intent.

Each threat section carries a **Limitations** subsection. Those are not
disclaimers — they are the accurate boundary of what FreeDroid protects against,
and they are the most useful part of the document. A threat model that reads as
though everything is covered is a threat model nobody checked.

### Security principles

1. **Extend Android's security model; do not replace it.** A parallel enforcement
   mechanism is a second thing that can disagree with the first. Disagreement
   between two authorities on the same question is a vulnerability class.
2. **Freedom of installation is not freedom from the sandbox.** Users choose
   sources. The sandbox applies identically regardless of source.
3. **No security decision depends on where an app came from.** Provenance changes
   what the user is told, never what the system permits.
4. **Development conveniences are gated, not remembered.** A build gate enforces
   the `user`/`userdebug` split.
5. **State limitations honestly.** FreeDroid will not claim to detect malware it
   cannot detect.

### Trust boundaries

```text
┌──────────────────────────────────────────────────────────────┐
│ UNTRUSTED  third-party apps, app stores, repositories,       │
│            downloaded APKs, USB hosts, network peers         │
├──────────────────────────────────────────────────────────────┤
│ SEMI       FreeDroid Store, first-party apps                 │
│ TRUSTED    — same sandbox, same permissions, no exemptions   │
├──────────────────────────────────────────────────────────────┤
│ TRUSTED    Android framework, system services, HALs          │
├──────────────────────────────────────────────────────────────┤
│ ROOT OF    Verified Boot chain, hardware keystore (TEE/      │
│ TRUST      StrongBox), bootloader, release signing keys      │
└──────────────────────────────────────────────────────────────┘
```

The Store sitting in SEMI-TRUSTED rather than TRUSTED is deliberate and is the
load-bearing decision of this model. If FreeDroid's own store needed privileges
that a third-party store cannot have, the multi-source promise would be
decorative.

---

## T1 — Malicious APKs

**Threat.** A user installs an application that is hostile by design: it
exfiltrates data, defrauds, spies, ransoms, or abuses accessibility and overlay
APIs to attack other apps.

**This is FreeDroid's highest-likelihood threat.** Allowing installation from
arbitrary sources increases exposure to it. The honest position is that FreeDroid
constrains what such an app can reach; it does not prevent the user from
installing it.

### Mitigations

| Mechanism | Effect |
| --- | --- |
| **Application sandbox** | Unique UID per app; private data directory unreadable by other apps; per-app SELinux context (`untrusted_app`). Kernel-enforced, not framework-enforced. |
| **Runtime permissions** | Camera, microphone, location, contacts, media, notifications, nearby devices, sensors are all user-granted at use time and revocable. |
| **Scoped storage** | No arbitrary filesystem access. Photo Picker grants per-item media access with no permission at all. |
| **Background restrictions** | Background start limits, foreground-service type declarations, background location requiring a separate two-step grant. |
| **Unused-app hibernation** | Permissions auto-revoked and the app stopped after prolonged disuse. |
| **Privacy indicators** | Camera/mic use shows a persistent indicator; global toggles hard-disable both. |
| **`targetSdk` enforcement** | Android 16 blocks installation of apps targeting below the minimum SDK, closing legacy-permission-model bypasses. |
| **Overlay/accessibility restrictions** | Restricted settings require an explicit, friction-laden grant flow for sideloaded apps — AOSP behavior, preserved. |
| **FreeDroid: provenance display** | Install confirmation shows the source and the user's prior trust decision for it. |
| **FreeDroid: permission preview** | Requested permissions and their sensitivity shown *before* installation, not only at first use. |
| **FreeDroid: signal surfacing** | Signature novelty, permission breadth, reproducible-build attestation where available. |

### Limitations — read this part

- **FreeDroid cannot determine whether an application is malicious.** Deciding
  this in general is undecidable; in practice, static and dynamic analysis are
  routinely evaded. Every signal FreeDroid shows is advisory. The UI will say
  "nothing known-bad found," never "safe."
- **User consent is the weakest link and cannot be engineered away.** A user who
  grants accessibility, overlay, notification-listener, or device-admin access to
  a hostile app has handed it most of what it needs. Friction helps; it does not
  solve.
- **Permitted capabilities suffice for real harm.** Network access requires no
  runtime permission. An app granted contacts and network can exfiltrate contacts.
  That is the permission model working as designed, not a bypass.
- **Side channels remain.** Installed-package enumeration (narrowed but not
  eliminated), timing, sensor-based inference, and shared-resource channels are
  ongoing research areas with no complete mitigation.
- **Kernel and framework 0-days bypass all of the above.** Mitigated only by
  patch cadence (§T4) — which means a lapsed SPL is a sandbox failure, not a
  paperwork failure.

---

## T2 — Compromised applications

**Threat.** A legitimate application is exploited at runtime (malicious input,
hostile ad SDK, compromised update) and the attacker executes code with that
app's privileges.

### Mitigations

| Layer | Containment |
| --- | --- |
| **UID isolation** | Attacker is confined to that app's UID. Other apps' data is unreadable by DAC. |
| **SELinux** | `untrusted_app` domain restricts reachable files, sockets, services, and ioctls — it constrains even code running as that UID. **Enforcing in every FreeDroid build variant, including `eng`.** |
| **seccomp-bpf** | Syscall filter narrows the kernel attack surface available for escalation. |
| **`isolatedProcess`, WebView sandbox** | Renderer compromise is contained below app privilege. |
| **`W^X` / no dynamic code** | Executable-only-from-read-only-storage enforcement blocks common code-injection paths. |
| **Hardware mitigations** | PAC/BTI, MTE where the SoC provides them (arm64 targets, Phase 10+). |
| **Keystore** | Hardware-backed keys are not extractable by compromised app code; use is constrained by auth-binding. |
| **Permission scope** | The attacker inherits only permissions the user already granted, not the union of what is grantable. |

### Limitations

- Everything the app legitimately holds is forfeit: its own data, its granted
  permissions, its network access, its notification and accessibility reach.
- **A sandbox escape defeats this layer entirely.** Containment assumes kernel and
  SELinux integrity.
- MTE, PAC, and BTI depend on the SoC and are absent on Cuttlefish x86_64.
  Phase 1–3 testing therefore validates *policy*, not hardware mitigation.
- Confused-deputy attacks through exported components, implicit intents, and
  `PendingIntent` misuse remain the responsibility of individual apps. FreeDroid
  cannot fix a third-party app's IPC bugs.

---

## T3 — Privilege escalation

**Threat.** An ordinary application obtains system, root, or kernel privileges.

**This is the threat that FreeDroid's design most directly commits to.** Allowing
installation from arbitrary sources is only defensible if an installed app cannot
escape the sandbox.

### Mitigations

| Control | Detail |
| --- | --- |
| **No root for apps. Ever.** | No `su` binary, no root helper, no developer toggle. Present in no build variant. |
| **No universal privileged API** | There is no FreeDroid interface through which an ordinary app performs a privileged operation on its own behalf. Each FreeDroid service exposes a narrow, permission-guarded Binder interface or none. |
| **`signature`/`privileged` permissions** | FreeDroid system services guard interfaces with `signature`-level permissions. Third-party apps cannot hold them — they are not signed by the platform key. |
| **Privileged permission allowlist** | AOSP requires every privileged app permission to be explicitly allowlisted in `/etc/permissions/`. FreeDroid's allowlist is reviewed line by line each release; an unallowlisted grant fails the build. |
| **SELinux `neverallow`** | Compile-time assertions that `untrusted_app` cannot reach system domains. Violations fail the policy build. CTS/VTS additionally assert them at runtime. |
| **Read-only system partitions** | `/system`, `/product`, `/vendor` mounted read-only, dm-verity protected. No runtime write path exists for any app. |
| **`INSTALL_PACKAGES` withheld** | Not granted to any app on production builds — **including the FreeDroid Store**, which uses `REQUEST_INSTALL_PACKAGES` with user confirmation like every other installer. |
| **Service least privilege** | Each FreeDroid service runs in its own SELinux domain with a minimal, reviewed policy. `SourceTrustService` reads and writes trust records and nothing else; notably, it cannot install packages. |
| **Zygote/`init` discipline** | No new `init` services with broad capability sets. Any addition requires security review. |

### FreeDroid-specific escalation risks

The features FreeDroid adds are exactly where this threat would materialize:

| Risk | Control |
| --- | --- |
| Store granted `INSTALL_PACKAGES` "for convenience" | Explicitly prohibited. Automated check in the release gate. |
| `SourceTrustService` exposing a trust-granting call to apps | Interface is `signature`-guarded and read-mostly. Trust changes originate only from a Settings UI flow with user interaction. An app cannot mark itself trusted. |
| `UpdateService` exposing OTA installation to apps | No app-reachable interface installs an update. Signature verification is unconditional (§T4). |
| Framework extensions widening `framework.jar` | FreeDroid APIs ship as a **separate SDK library**, not merged into `framework.jar`, so they add no surface to the core framework. |

### Limitations

- **Kernel and TEE vulnerabilities defeat all of this.** Escalation chains are
  found regularly in every mobile platform. Patch cadence (§T4) is the only
  mitigation, and a device on a stale SPL is genuinely exposed.
- Vendor HAL and driver code is frequently the weakest link and is largely
  outside FreeDroid's control on a given SoC.
- A malicious or careless FreeDroid service — ours — could introduce an
  escalation path. Mitigated by review and by keeping the service count low;
  not eliminated. Each new system service is a permanent addition to the attack
  surface and should be treated as a cost.
- `userdebug` builds permit root via ADB by design. **They are not production**
  and must not be distributed. The release gate exists because this is precisely
  the kind of thing that ships by accident.

---

## T4 — Malicious OTA packages

**Threat.** An attacker delivers a crafted system update — via DNS hijack, a
compromised CDN, a hostile network, or a compromised update server — and takes
over the device at the OS level.

### Mitigations

| Stage | Control |
| --- | --- |
| **Package signing** | Every OTA is signed with the FreeDroid release key. `payload.bin` carries its own signature verified by `update_engine`. |
| **On-device verification** | The recovery/`update_engine` path verifies against a public key **baked into the read-only, Verified-Boot-protected system image**. An attacker must break signing, not the transport. |
| **Transport is not trust** | HTTPS with certificate validation is used, but a valid TLS session confers no update authority. An update from the official URL with a bad signature is rejected identically to one from anywhere else. |
| **Rollback index** | Each release carries a monotonically increasing AVB rollback index. The bootloader refuses images below the stored index (§T5). |
| **A/B (Virtual A/B) updates** | Applied to the inactive slot. A failed update boots the previous slot; the device does not brick. |
| **Post-install verification** | dm-verity verifies the new slot at boot. Corruption or tampering prevents boot and triggers fallback. |
| **Staged updates** | Applied at reboot from a verified staging area, not live-patched into a running system. |
| **Security-only channel** | `android16-security-release` merges ship as minimal-diff updates independent of feature releases (requirement 15). |
| **Metadata integrity** | The update manifest is signed. Version, target device, and SPL are checked before download begins. |
| **APEX updates** | Without Google Play, FreeDroid delivers Mainline module updates itself — signed APEX installs or OTA-bundled. **This is required, not optional:** a meaningful share of monthly fixes lands in APEX modules, and a Google-free device that does not ship them silently misses them. |

### Limitations

- **Compromise of the release signing key defeats everything here.** This is the
  single highest-consequence risk in the project. Controls in §T10.
- Downgrade to an *older but still signed* release is prevented only where the
  hardware supports AVB rollback indexes in tamper-evident storage. Cuttlefish
  and many devices with unlocked bootloaders do not.
- An attacker controlling the network can **withhold** updates. Signature checks
  prevent malicious updates, not denial of updates. Mitigation: the device
  surfaces its SPL age and warns when it falls behind.
- A malicious but correctly signed update — an insider or a compromised build
  pipeline — passes every on-device check. Addressed only by §T10 controls
  (reproducible builds, multi-party release approval, transparency logging).
- Updates cannot fix a device whose bootloader is compromised below the AVB root.

---

## T5 — Modified system images

**Threat.** An attacker with physical access, or one who achieved root, modifies
the system partition, boot image, or kernel to install a persistent backdoor.

### Mitigations

| Layer | Control |
| --- | --- |
| **AVB 2.0 chain** | Bootloader verifies `vbmeta`, which chains to every partition's hash/hashtree descriptor. The chain is rooted in a key in tamper-evident hardware storage. |
| **dm-verity** | `/system`, `/product`, `/vendor` are verified block-by-block **at read time**, not once at boot. A modified block fails verification when read, not just at startup. |
| **Rollback protection** | Rollback index in tamper-evident storage; images below the stored index are refused. Blocks downgrade-to-vulnerable-version attacks. |
| **Read-only mounts** | No runtime write path to verified partitions. `adb remount` is unavailable on `user` builds. |
| **Boot state attestation** | GREEN (locked, our key) / YELLOW (locked, user key) / ORANGE (unlocked) / RED (verification failed). ORANGE and YELLOW display a mandatory warning at every boot. |
| **Key attestation** | Apps may verify boot state and patch level via hardware-backed key attestation and check them against their own policy. |
| **Unlock wipes data** | Unlocking the bootloader forces a factory reset, so unlocking cannot be used to reach existing user data (§T6). |

### Limitations

- **An unlocked bootloader disables this entire section.** FreeDroid supports
  user-controlled unlocking as a matter of ownership — but an unlocked device has
  no image-integrity guarantee, and the boot warning states so. Users who
  re-lock with their own AVB key regain a verified chain rooted in *their* key
  (YELLOW).
- **Verified Boot proves provenance, not absence of vulnerabilities.** A
  correctly signed image with an exploitable bug verifies perfectly.
- Below-the-bootloader compromise (boot ROM, SoC, firmware implants) is out of
  scope. Nothing above the bootloader can detect it.
- Devices that cannot be re-locked with a custom AVB key cannot run FreeDroid
  with a full verified chain. **This is a hard selection criterion for reference
  hardware in Phase 10**, not a detail to discover late.
- dm-verity protects integrity, not confidentiality. System partition contents
  are readable by anyone with physical access.

---

## T6 — Physical attacks

**Threat.** An attacker has physical possession and attempts to extract data,
install persistent malware, or bypass authentication. Includes forensic
extraction tooling and "evil maid" scenarios.

### Mitigations

| Control | Detail |
| --- | --- |
| **File-Based Encryption (FBE)** | Credential Encrypted (CE) storage is inaccessible until first unlock after boot. Device Encrypted (DE) storage covers only what must work pre-unlock. |
| **Metadata encryption** | Everything outside FBE scope is encrypted, so filesystem structure is not exposed. |
| **Hardware-bound keys** | Keys derive from a hardware secret plus user credential and never leave the TEE/StrongBox. Data is not decryptable off-device. |
| **Brute-force resistance** | Key derivation is rate-limited by hardware (Gatekeeper/Weaver). Attempts cannot be parallelized off-device. Escalating lockouts. |
| **Before First Unlock (BFU)** | After a cold boot, user data keys are not in memory. This is the strongest state and defeats most forensic extraction. |
| **Verified Boot** | Prevents persistent modification on a locked device (§T5). |
| **Unlock wipes data** | Bootloader unlock triggers a mandatory factory reset. |
| **ADB off by default** | No USB debugging surface on a production device (§T8). |
| **Lockdown mode** | User-invokable state disabling biometrics and notifications until the PIN/password is entered. |
| **Auto-reboot to BFU** | FreeDroid will evaluate an inactivity-triggered reboot that returns the device to the BFU state, following published prior art. Candidate feature, Phase 9. |

### Limitations

- **After First Unlock (AFU) is substantially weaker.** Keys are resident in
  memory, and commercial forensic tools target this state specifically. A device
  seized while powered on and previously unlocked is at meaningful risk.
- Weak credentials defeat encryption. A 4-digit PIN has 10,000 possibilities;
  hardware rate-limiting makes this slow but not impossible. FreeDroid will
  encourage strong credentials and must not overstate what encryption achieves
  with a weak one.
- **Biometrics are convenience, not security.** Face and fingerprint unlock are
  bypassable with varying effort and are legally distinct from a passphrase in
  some jurisdictions. Lockdown mode exists for this reason.
- Hardware attacks — chip-off, fault injection, glitching, side channels,
  undisclosed TEE vulnerabilities — are outside what an OS can mitigate.
- A well-resourced attacker with unlimited physical access and a
  device-specific exploit chain generally wins. FreeDroid raises cost; it does
  not promise immunity.

---

## T7 — Stolen devices

**Threat.** The device is lost or stolen. The attacker wants user data or wants
to resell the device.

### Mitigations

| Control | Detail |
| --- | --- |
| **Encryption at rest** | FBE as in §T6. Data is not readable without the user credential. |
| **Lock screen enforcement** | Required for credential-derived keys. No lock screen means no meaningful encryption — FreeDroid will state this plainly during setup rather than burying it. |
| **Rate-limited unlock** | Hardware-backed lockouts on repeated failures. |
| **Factory Reset Protection (FRP)** | A wiped device remains bound to its prior credential, removing resale value. AOSP-level FRP, no Google account required. |
| **Notification redaction** | Sensitive notification content hidden on the lock screen by default. |
| **Lockdown mode** | Disables biometrics and notification content immediately. |
| **Remote wipe** | Requires a management or find-my-device service. AOSP provides Device Policy APIs; FreeDroid ships no remote-wipe service by default, and this gap is stated rather than implied. |

### Limitations

- A device stolen **while unlocked** offers the thief everything the user has
  open. Encryption is irrelevant in that state. Short screen-timeout defaults
  help marginally.
- AFU weakness (§T6) applies.
- No remote wipe or device location by default. This is a real gap created by the
  Google-free default. Users needing it must adopt a third-party or self-hosted
  service; FreeDroid should document options rather than pretend the need is met.
- FRP is bypassable on some devices via vendor-specific flaws; it deters casual
  resale, not a determined refurbisher.
- Data already synced to cloud services is governed by those services, not by
  FreeDroid.

---

## T8 — Malicious USB connections

**Threat.** "Juice jacking," malicious charging stations, forensic extraction
docks, BadUSB-style peripherals, and unauthorized ADB access.

### Mitigations

| Control | Detail |
| --- | --- |
| **ADB disabled by default on `user` builds** | `ro.adb.secure=1`, `ro.debuggable=0`. Developer Options itself is hidden until deliberately revealed. Requirement 14. |
| **ADB authorization** | Even when enabled, a new host must be authorized by an on-device prompt showing the host key fingerprint. Unauthorized hosts get nothing. |
| **Authorization is not permanent** | Revocable, and revoked on factory reset. FreeDroid will default "always allow from this computer" to off. |
| **USB data blocked while locked** | AOSP restricts new USB data connections while the device is locked. Charging still works. |
| **Default USB mode: charge only** | Data transfer (MTP/PTP) requires explicit selection after unlock. |
| **Wireless ADB pairing** | Requires a pairing code; not silently reachable. |
| **Accessory/peripheral prompts** | USB accessory access requires user approval per accessory. |
| **`user` build has no root ADB** | `adb root` and `adb remount` are unavailable regardless of ADB state. |

### Limitations

- **A user who enables ADB and authorizes a hostile host has granted deep
  access** — app installation, data extraction from debuggable apps, logcat. No
  OS setting overrides an explicit user decision here.
- USB stack and kernel driver vulnerabilities can be reachable before any
  authorization prompt. This is a recurring CVE class across all mobile
  platforms; mitigation is patch cadence.
- Malicious peripherals (HID injection) can act as a keyboard on an unlocked
  device.
- `userdebug` builds have ADB enabled and root available. **Never distribute
  them.** The release gate enforces the split.
- Charge-only mode does not protect against attacks on the charging negotiation
  layer itself (USB-PD firmware).

---

## T9 — Compromised repositories

**Threat.** A third-party app store or repository is compromised, or is hostile
from the outset, and serves malicious or backdoored applications.

**This threat is created by FreeDroid's core feature.** Supporting multiple
distribution sources means supporting sources we do not control and cannot audit.

### Mitigations

| Control | Detail |
| --- | --- |
| **Repository metadata signing** | Repository indexes must be signed; the signing key is pinned when the user adds the repository. TOFU, with key change requiring explicit re-approval. |
| **APK signature verification is unconditional** | Performed by `PackageManagerService` regardless of source. A compromised repository cannot serve an APK that fails verification. |
| **Update signature continuity** | An update must be signed by the same key as the installed app (or a valid v3 proof-of-rotation chain). **A compromised repository cannot push a backdoored update to an app it did not originally sign** — this is the single strongest control in this section. |
| **Per-source trust** | Each source is added deliberately, is individually revocable, and has its own trust record in `SourceTrustService`. |
| **Source provenance at install** | The confirmation dialog names the source and the user's prior decision about it. `PackageInstaller.setPackageSource()` carries this through the platform. |
| **No source-conditional relaxation** | There is no source for which verification is weakened. Trust affects warning copy only. |
| **Sandbox applies equally** | Whatever a repository serves runs as `untrusted_app` (§T1, §T2). |
| **Reproducible-build attestation** | Where a repository publishes it (as F-Droid does for some apps), FreeDroid surfaces it. It is a meaningful signal — and still not a safety guarantee. |
| **Revocation propagation** | Removing a source stops future updates from it and warns about apps already installed from it. FreeDroid will not silently uninstall the user's apps. |

### Limitations

- **A compromised repository can serve genuinely malicious apps that verify
  perfectly.** Signature verification proves *who signed*, never *what the code
  does*. If a hostile developer signs hostile code, every check passes.
- **A compromised repository can push malicious updates to apps it legitimately
  signs.** Continuity protects apps signed by *others*, not the repository's own.
- Compromise of an upstream *developer's* signing key defeats continuity entirely.
- TOFU key pinning is only as good as the first fetch. A user who adds a
  repository over a hostile network pins the attacker's key.
- FreeDroid cannot audit third-party repository operational security and will not
  claim to. Listing a repository in the UI must not read as endorsement.
- Repositories can serve different content to different clients. FreeDroid sees
  only what it is served.
- **Users can be socially engineered into adding hostile repositories.** This is
  the most likely realization of this threat, and UI friction is the only
  available mitigation.

---

## T10 — Supply-chain attacks

**Threat.** Compromise of source, dependencies, build infrastructure, signing
keys, or release artifacts — producing a malicious OS that passes every on-device
check because it is correctly signed.

**This is the highest-consequence threat in the project.** Every other mitigation
assumes the OS is what we intended to build.

### Source integrity

| Control | Detail |
| --- | --- |
| Pinned upstream | AOSP pinned to a release tag; `repo manifest -r -o` records every project SHA per release build. |
| Overlay structure | The complete FreeDroid delta is small and human-reviewable (ADR-0001). This is a security property: a delta hidden inside a vendored AOSP copy cannot be audited by inspection. |
| Mandatory review | No direct pushes to release branches. Every change reviewed by someone other than its author. |
| Signed commits and tags | Required for release branches. |
| Patch justification | Every upstream patch carries stated compatibility and security impact and a named owner. |

### Build integrity

| Control | Detail |
| --- | --- |
| Hermetic builds | AOSP prebuilt toolchains, not host toolchains. Pinned and hash-verified. |
| Dedicated build hosts | Release builds only on controlled infrastructure. Never a developer laptop. |
| Build provenance | Manifest SHAs, toolchain versions, build host identity, and timestamp recorded and archived with artifacts. |
| Reproducible builds | Goal, not yet a capability. AOSP is not fully reproducible out of the box; scope assessed after Phase 1. Independent rebuild verification is the strongest available answer to a compromised builder, and it should be pursued seriously rather than listed aspirationally. |
| Dependency pinning | Every non-AOSP dependency pinned by hash. New dependencies require review — each is a party that can compromise the OS. |

### Signing key protection

**The single highest-consequence asset in the project.**

| Control | Detail |
| --- | --- |
| **Never in version control** | No release key, in any form, in any repository. Enforced by CI secret scanning, not by convention. |
| **Hardware-backed custody** | Release keys in an HSM or on an offline, air-gapped signing host. |
| **Separation** | Distinct keys for platform, release/OTA, and APEX. Compromise of one does not imply the others. |
| **Multi-party authorization** | Release signing requires more than one authorized person. |
| **Audit logging** | Every signing operation logged, with the artifact digest recorded. |
| **Rotation plan** | Documented and rehearsed **before** it is needed. APK signature scheme v3 proof-of-rotation supports app key rotation; AVB key rotation on fielded devices is materially harder and must be planned for, not improvised. |
| **Test/release separation** | Test keys named unambiguously and rejected by the release gate for `user` builds. |

### Release artifact integrity

| Control | Detail |
| --- | --- |
| Signed artifacts | OTA packages and factory images signed; verification instructions published. |
| Published digests | SHA-256 for all artifacts, published over a separate channel from the artifacts themselves. |
| Transparency log | Append-only record of releases, so a user-specific targeted build is detectable by comparison. Design goal, Phase 8–9. |
| CDN is untrusted | Artifact integrity rests on signatures, never on CDN or TLS (§T4). |

### Limitations

- **A compromised signing key is catastrophic and largely unrecoverable** for
  fielded devices, especially where rollback protection prevents downgrading past
  a maliciously signed release.
- A malicious insider with release authority defeats most controls. Multi-party
  authorization raises the bar; collusion defeats it.
- **We depend on upstream AOSP's own supply chain.** A compromise at Google is
  outside our control and largely outside our detection.
- Compiler and toolchain trust ("Reflections on Trusting Trust") is not fully
  solvable. Reproducible builds with independent verification is the best
  available answer and is not yet implemented.
- Build infrastructure compromise produces correctly signed malicious images.
  Only reproducible builds plus independent rebuilds detect this.
- Third-party dependencies each represent an additional party capable of
  compromising the OS. Minimizing dependency count is a security control, not
  merely hygiene.

---

## Cross-cutting: development / production isolation

The failure mode this section exists to prevent: a debug setting reaching a
production image because someone intended to revert it and did not.

| Setting | `eng` | `userdebug` | `user` (production) |
| --- | --- | --- | --- |
| `ro.debuggable` | 1 | 1 | **0** |
| ADB | on | on, authorized | **off by default** |
| `adb root` / `remount` | available | available | **unavailable** |
| SELinux | **enforcing** | **enforcing** | **enforcing** |
| Package signature verification | **on** | **on** | **on** |
| Verified Boot | test keys | test keys | **release keys** |
| dm-verity | may be off | may be off | **on** |
| Signing key | test | test | **release (HSM/offline)** |
| Test/debug apps | present | present | **absent** |

**Enforcement.** `scripts/verify-build-variant.sh` runs as a mandatory release
gate and fails the build if a `user` image contains any debug setting above, is
signed with a test key, or contains a permissive SELinux domain. A checklist is
not a control; the gate is the control.

---

## Verification status

**Every row below is currently unverified.** This table is the honest state of
the project and updates as tests in [`TESTING.md`](../development/TESTING.md) run.

| # | Claim | Verified by | Status |
| --- | --- | --- | --- |
| T1 | Sandbox isolates app data | SEC-03 cross-app access test | ⬜ not tested |
| T2 | SELinux enforcing in all variants | SEC-01 `getenforce` on booted image | ⬜ not tested |
| T3 | App cannot obtain system privilege | SEC-02 privileged-permission denial | ⬜ not tested |
| T3 | No app holds `INSTALL_PACKAGES` | SEC-07 permission audit | ⬜ not tested |
| T4 | OTA rejects invalid signature | SEC-05 tampered-payload test | ⬜ not tested |
| T5 | Verified Boot detects modification | SEC-06 modified-image boot test | ⬜ not tested |
| T5 | Rollback protection blocks downgrade | SEC-08 rollback index test | ⬜ not tested |
| T8 | ADB off by default on `user` | SEC-04 fresh `user` image test | ⬜ not tested |
| T9 | Signature verified regardless of source | SEC-09 multi-source install test | ⬜ not tested |
| — | Dev settings absent from `user` | Release gate | ⬜ not implemented |

## Review cadence

- Re-reviewed each Android major release adoption.
- Re-reviewed whenever a FreeDroid system service or privileged component is
  added or changed.
- Formal assessment in Phase 9, before any production consideration.
