# FreeDroid — Testing Strategy

**Phase:** 0 (Environment + Architecture)
**Status:** **No test has been executed.** Every test below is defined but unrun.

---

## 1. The governing rule

> **Configuration values alone are not evidence that a security mechanism works.**

`BOARD_SELINUX_ENFORCING := true` in a makefile proves that someone wrote a line
in a makefile. `getenforce` returning `Enforcing` on a booted image proves SELinux
is enforcing. Only the second is evidence.

This applies to every claim in
[`SECURITY_MODEL.md`](../security/SECURITY_MODEL.md). Its Part IV verification
table is the authoritative record of what has actually been demonstrated, and
every row currently reads **not tested**.

Three corollaries:

1. **Never disable a security check to make a test pass.** A failing security test
   is a finding. Silencing it converts a finding into a latent vulnerability plus
   a false green build.
2. **Never hide a build or test failure.** Capture the exact error, find the root
   cause, fix it once.
3. **A passing emulator test is not a hardware result.** Cuttlefish demonstrates
   policy and API behavior. Hardware-rooted properties — Verified Boot, rollback
   protection, hardware Keystore, real encryption key custody — are demonstrated
   only on physical hardware in Phase 10.

---

## 2. Build tests — BLD

| ID | Test | Pass criterion |
| --- | --- | --- |
| BLD-01 | **Clean build** from a synced tree | Completes with no errors; build time recorded |
| BLD-02 | **Incremental build** after a one-file change | Completes; only affected modules rebuild |
| BLD-03 | `installclean` after a target switch | Completes without a full rebuild |
| BLD-04 | **System image generation** | All images produced; sizes within partition limits |
| BLD-05 | **Emulator (Cuttlefish) boot** | Reaches the home screen; boot time recorded |
| BLD-06 | Build reproducibility | Identical inputs produce identical artifacts — **aspirational**; AOSP is not reproducible out of the box |
| BLD-07 | All three variants build | `eng`, `userdebug`, `user` all succeed |
| BLD-08 | Tablet target builds and boots | `freedroid_cf_x86_64_tablet` reaches the home screen |

BLD-01 and BLD-05 run against **unmodified AOSP** in Phase 1 to establish the
baseline. Every later result is meaningful only by comparison to it.

---

## 3. Application tests — APP

Run on every build against a fixed corpus of real third-party APKs (F-Droid
sourced, licensing permitting) plus purpose-built test apps.

### Installation

| ID | Test | Pass criterion |
| --- | --- | --- |
| APP-01 | Install via `PackageInstaller` | Installs; appears in launcher |
| APP-02 | Install via `adb install` | Installs (`userdebug` only) |
| APP-03 | Install from the FreeDroid Store | Installs; source recorded as the Store |
| APP-04 | Install from a third-party store | Installs; source recorded correctly |
| APP-05 | **Direct APK** from a file | Installs after user confirmation; bypasses nothing |

### Execution

| ID | Test | Pass criterion |
| --- | --- | --- |
| APP-06 | Launch | Main activity starts; no crash |
| APP-07 | Native (NDK) app | Loads and runs; ABI intact |
| APP-08 | Multi-window / split-screen | Resizes correctly; state preserved |
| APP-09 | Configuration change | Survives rotation and window resize |
| APP-10 | Background execution limits | Doze and App Standby apply |
| APP-11 | Notifications | Delivered; `POST_NOTIFICATIONS` enforced |

### Updates

| ID | Test | Pass criterion |
| --- | --- | --- |
| APP-12 | Update, **same signature** | Succeeds; app data preserved |
| APP-13 | Update, **different signature** | **REJECTED.** Non-negotiable. |
| APP-14 | Update via v3 key rotation | Accepted with a valid proof-of-rotation chain |
| APP-15 | Update across sources | Continuity enforced regardless of which source delivers it |

### Uninstall

| ID | Test | Pass criterion |
| --- | --- | --- |
| APP-16 | Uninstall | App removed; app data removed |
| APP-17 | Uninstall with retained data | Behaves per AOSP semantics |

### Permissions

| ID | Test | Pass criterion |
| --- | --- | --- |
| APP-18 | Runtime permission grant | Dialog shown; grant takes effect |
| APP-19 | Runtime permission denial | Denial respected; app handles it |
| APP-20 | Permission revocation | Revoked at runtime; access stops immediately |
| APP-21 | One-time permission | Expires as AOSP specifies |
| APP-22 | Hibernation / auto-revoke | Permissions revoked after disuse |

**APP-13 is the single most important application test.** A failure there means
update signature continuity does not hold, and the multi-source design is unsafe.

---

## 4. Security tests — SEC

One-to-one with the verification table in `SECURITY_MODEL.md` Part IV.

| ID | Test | Method | Pass criterion | Requires |
| --- | --- | --- | --- | --- |
| **SEC-01** | **SELinux enforcing** | `adb shell getenforce`, every variant | `Enforcing`. Anything else is a release blocker. | Cuttlefish |
| **SEC-02** | No privilege escalation | Test app attempts `INSTALL_PACKAGES`, system file writes, privileged service calls | All denied; denials logged | Cuttlefish |
| **SEC-03** | **Sandbox isolation** | App A reads app B's data dir — by path **and** via content provider | Denied by DAC **and** SELinux. Verify both; either alone is not the property. | Cuttlefish |
| **SEC-04** | **ADB off by default** | Fresh `user` image, USB connected, no prior setup | No ADB device; no authorization prompt | Cuttlefish |
| **SEC-05** | **OTA signature validation** | Apply OTA with (a) tampered payload, (b) wrong-key signature, (c) stripped signature | All three rejected; device unchanged | Cuttlefish |
| **SEC-06** | **Verified Boot detection** | Modify a byte in `system.img`; attempt boot on a locked device | Boot fails or falls back; state not GREEN | **Hardware** |
| **SEC-07** | Privileged permission audit | Enumerate every app holding privileged or `signature` permissions | Matches the reviewed allowlist exactly; **no app holds `INSTALL_PACKAGES`** | Cuttlefish |
| **SEC-08** | **Rollback protection** | Install a release with a lower rollback index | Refused by the bootloader | **Hardware (RPMB)** |
| **SEC-09** | Source-independent verification | Same tampered APK from Store, third-party store, and direct file | Rejected identically in all three cases | Cuttlefish |
| **SEC-10** | Signature scheme enforcement | Install v1-only, v2, v3, and corrupted-signature APKs | v2/v3 accepted; v1-only and corrupted rejected per AOSP `targetSdk` rules | Cuttlefish |
| **SEC-11** | System partitions read-only | Write attempts to `/system`, `/product`, `/vendor` as app and as root | Denied for the app; `user` build has no write path at all | Cuttlefish |
| **SEC-12** | **Encryption (FBE)** | Inspect userdata before first unlock; attempt CE access | Encrypted; CE inaccessible pre-unlock | **Hardware (TEE)** |
| **SEC-13** | **Hardware Keystore** | Generate a hardware-backed key; attempt extraction | Not extractable; attestation reports hardware backing | **Hardware (TEE/StrongBox)** |
| **SEC-14** | Permission enforcement | Access protected APIs without the permission | `SecurityException` every time | Cuttlefish |
| **SEC-15** | SELinux denial audit | Normal operation across all system apps | Zero unexpected denials in `dmesg`/`logcat` | Cuttlefish |
| **SEC-16** | **Release gate** | Run `verify-build-variant.sh` on a `user` image, **and on an image with an injected debug setting** | Passes the good image; **fails the bad one** | Build host |
| **SEC-17** | `SourceTrustService` privilege | App attempts to mark itself or another source trusted | Denied; trust changes only via the Settings UI flow | Cuttlefish |
| **SEC-18** | **No root available** | Attempt `su`, root helper discovery, root-requiring operations on `user` | Nothing available | Cuttlefish |
| **SEC-19** | ADB authorization | Connect an unauthorized host; connect after revoking | Prompt shown with key fingerprint; revoked host denied | Cuttlefish |
| **SEC-20** | USB locked-device restriction | Connect USB data while locked | New data connections blocked; charging works | Cuttlefish |

**SEC-16 deserves emphasis:** a gate that has never been observed to fail is a
gate nobody has shown to work. It must be tested with a deliberately bad image.

**Hardware-marked tests cannot be demonstrated on Cuttlefish.** An emulator run
of SEC-06, SEC-08, SEC-12, or SEC-13 shows API behavior, not hardware protection,
and must not be recorded as verifying the property.

---

## 5. Device tests — DEV

Phase 10+ on physical hardware. Cuttlefish covers a subset only.

| ID | Area | Test | Phone | Tablet |
| --- | --- | --- | --- | --- |
| DEV-01 | Display | Resolution, density, refresh rate, rotation, brightness | ✅ | ✅ |
| DEV-02 | Touch | Multi-touch, gestures, palm rejection, stylus | ✅ | ✅ |
| DEV-03 | Wi-Fi | Scan, WPA2/WPA3 connect, throughput, roaming | ✅ | ✅ |
| DEV-04 | Bluetooth | Pair, audio profile, BLE, multi-device | ✅ | ✅ |
| DEV-05 | Camera | Photo, video, front/rear, flash, Camera2/CameraX | ✅ | ✅ |
| DEV-06 | Audio | Playback, capture, routing, volume, headset, call audio | ✅ | ✅ |
| DEV-07 | USB | Charging, MTP, accessory, ADB where enabled | ✅ | ✅ |
| DEV-08 | Battery | Charge, discharge curve, idle drain, thermal | ✅ | ✅ |
| DEV-09 | Sensors | Accelerometer, gyroscope, magnetometer, proximity, light | ✅ | ✅ |
| DEV-10 | GPS | Cold and warm fix, accuracy, indoor fallback | ✅ | where present |
| DEV-11 | Cellular | Registration, voice, SMS, data, VoLTE, roaming | ✅ | where present |
| DEV-12 | NFC | Tag read/write, HCE, P2P | where present | where present |
| DEV-13 | Biometrics | Enrollment, unlock, Keystore auth binding, lockout | ✅ | where present |
| DEV-14 | Fold/unfold | State preserved across the hinge transition | foldables | — |
| DEV-15 | External display | Multi-display, freeform windows | ✅ | ✅ |
| DEV-16 | Large-screen layout | All window size classes render correctly | ✅ | ✅ |

**Absent hardware must degrade cleanly, not break.** A Wi-Fi-only tablet must not
present a broken dialer (DEV-11).

---

## 6. Compatibility tests — CTS

| Gate | Requirement |
| --- | --- |
| **CTS baseline** | Run against **unmodified AOSP** in Phase 1. "CTS passes" is meaningless without knowing what the baseline passed. |
| **CTS regression** | Every FreeDroid build compared against that baseline. Any new failure is a release blocker. |
| **CTS Verifier** | Manual hardware tests, Phase 10+. |
| **Real-app corpus** | Fixed APK set: install, launch, permissions, update, uninstall. |
| **NDK ABI** | Native sample apps verifying ABI stability. |

---

## 7. Execution matrix

| Test class | Every commit | Nightly | Pre-release | Phase gate |
| --- | --- | --- | --- | --- |
| BLD | ✅ incremental | ✅ clean | ✅ | ✅ |
| APP | subset | ✅ | ✅ | ✅ |
| **SEC** | **✅ always** | ✅ | ✅ | ✅ |
| DEV | — | — | ✅ | ✅ (Phase 10+) |
| CTS | — | ✅ | ✅ | ✅ |

Security tests run on **every** build. They are the cheapest place to catch a
regression that would otherwise ship, and the tests most likely to be skipped
under schedule pressure — which is exactly why they are unconditional.

---

## 8. Release blockers

A release does not ship if any of these hold:

- Any SEC test fails
- CTS regression versus the baseline
- **APP-13** (different-signature update) does not reject
- The release gate (SEC-16) fails, or has never been shown to fail on a bad image
- Any SELinux domain is permissive
- Any debug setting present in a `user` image
- Any test-key signature in a release artifact
- An unexplained SELinux denial in normal operation

---

## 9. Reporting

Results are recorded per build with the build ID, the manifest SHA record
(`repo manifest -r`), and the variant, and archived with release artifacts.

The verification table in `SECURITY_MODEL.md` Part IV is updated from **actual
results, never in advance of them**. A row moves from ⬜ to ✅ when a test has run
and passed — not when the feature has been implemented.
