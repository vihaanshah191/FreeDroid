# FreeDroid — Testing Strategy

**Status:** No tests have been run. Every test below is defined but unexecuted.

---

## 1. The governing rule

> **Do not claim a security feature works because a configuration file contains
> the expected setting. Test the actual behavior.**

`BOARD_SELINUX_ENFORCING := true` in a makefile proves that someone wrote a line
in a makefile. `getenforce` returning `Enforcing` on a booted image proves SELinux
is enforcing. Only the second is evidence.

This applies to every claim in [`SECURITY_MODEL.md`](../security/SECURITY_MODEL.md).
Its verification table is the authoritative record of what has actually been
demonstrated, and it currently reads "not tested" for every row.

Two corollaries:

- **Never disable a security check to make a test pass.** A failing security test
  is a finding. Silencing it converts a finding into a latent vulnerability plus a
  false green build.
- **Never hide a build or test failure.** Capture the exact error, find the root
  cause, fix it once.

## 2. Build tests — BLD

| ID | Test | Pass criterion |
| --- | --- | --- |
| BLD-01 | Clean build from synced tree | Completes with no errors; build time recorded |
| BLD-02 | Incremental build after a one-file change | Completes; only affected modules rebuild |
| BLD-03 | `installclean` after target switch | Completes without a full rebuild |
| BLD-04 | System image generation | All images produced; sizes within partition limits |
| BLD-05 | Emulator/Cuttlefish boot | Reaches the home screen; boot time recorded |
| BLD-06 | Build reproducibility | Two builds from identical input produce identical artifacts (**aspirational** — AOSP is not reproducible out of the box; scope assessed after Phase 1) |
| BLD-07 | All three variants build | `eng`, `userdebug`, `user` all succeed |

BLD-01 and BLD-05 run against **unmodified AOSP** in Phase 1 to establish the
baseline. Every later result is meaningful only by comparison to it.

## 3. Application tests — APP

Run on every build. The corpus is a fixed set of real third-party APKs
(F-Droid-sourced, licensing permitting) plus purpose-built test apps.

| ID | Test | Pass criterion |
| --- | --- | --- |
| APP-01 | Install via `PackageInstaller` | Installs; appears in launcher |
| APP-02 | Install via `adb install` | Installs (`userdebug` only) |
| APP-03 | Install from FreeDroid Store | Installs; source recorded as the Store |
| APP-04 | Install from a third-party store | Installs; source recorded correctly |
| APP-05 | Install a direct APK from a file | Installs after user confirmation |
| APP-06 | Launch | Main activity starts; no crash |
| APP-07 | Runtime permission grant | Dialog shown; grant takes effect |
| APP-08 | Runtime permission denial | Denial respected; app handles it |
| APP-09 | Permission revocation | Revoked at runtime; access stops |
| APP-10 | Update, same signature | Succeeds; data preserved |
| APP-11 | **Update, different signature** | **REJECTED.** Non-negotiable. |
| APP-12 | Update across sources | Continuity rules enforced regardless of source |
| APP-13 | Uninstall | Removed; app data removed |
| APP-14 | Background execution limits | Doze and standby apply |
| APP-15 | Notifications | Delivered; `POST_NOTIFICATIONS` enforced |
| APP-16 | Native (NDK) app | Loads and runs; ABI intact |
| APP-17 | Multi-window / split-screen | Resizes correctly; state preserved |
| APP-18 | Configuration change | Survives rotation and window resize |

APP-11 is the single most important application test. A failure there means the
update-continuity property in `SECURITY_MODEL.md` §T9 does not hold, and the
multi-source design is unsafe.

## 4. Security tests — SEC

These correspond one-to-one with the verification table in the security model.

| ID | Test | Method | Pass criterion |
| --- | --- | --- | --- |
| **SEC-01** | SELinux enforcing | `adb shell getenforce` on a booted image, every variant | `Enforcing`. Any other value is a release blocker. |
| **SEC-02** | App cannot obtain system privilege | Test app attempts privileged operations: `INSTALL_PACKAGES`, system file writes, privileged service calls | Every attempt denied; denials logged |
| **SEC-03** | Sandbox isolation | Test app A attempts to read app B's data directory, by path and by content provider | Denied by DAC **and** SELinux. Verify both — either alone is not the property. |
| **SEC-04** | ADB off by default | Fresh `user` image, USB connected, no prior setup | No ADB device; no authorization prompt |
| **SEC-05** | OTA signature validation | Apply an OTA with (a) a tampered payload, (b) a wrong-key signature, (c) a stripped signature | All three rejected; device unchanged |
| **SEC-06** | Verified Boot detection | Modify a byte in `system.img`, attempt boot on a locked device | Boot fails or falls back; state is not GREEN |
| **SEC-07** | Privileged permission audit | Enumerate every app holding privileged or `signature` permissions | Matches the reviewed allowlist exactly; **no app holds `INSTALL_PACKAGES`** |
| **SEC-08** | Rollback protection | Attempt to install a release with a lower rollback index | Rejected by the bootloader |
| **SEC-09** | Source-independent verification | Install the same tampered APK from Store, third-party store, and direct file | Rejected identically in all three cases |
| **SEC-10** | Signature scheme enforcement | Install APKs with v1-only, v2, v3, and corrupted signatures | v2/v3 accepted; v1-only and corrupted rejected per AOSP `targetSdk` rules |
| **SEC-11** | System partition read-only | Attempt writes to `/system`, `/product`, `/vendor` as root (`userdebug`) and as an app | Denied for the app; `user` build has no write path at all |
| **SEC-12** | FBE verification | Inspect userdata before first unlock; attempt CE file access | Encrypted; CE inaccessible pre-unlock |
| **SEC-13** | Keystore hardware binding | Generate a hardware-backed key; attempt extraction | Not extractable; attestation reports hardware backing |
| **SEC-14** | Permission enforcement | Access protected APIs without the permission | `SecurityException` in every case |
| **SEC-15** | SELinux denial audit | Normal operation across all system apps | Zero unexpected denials in `dmesg`/`logcat` |
| **SEC-16** | Release gate | Run `verify-build-variant.sh` on a `user` image | Passes; deliberately fails on an image with an injected debug setting — **test the gate itself, not just the image** |
| **SEC-17** | `SourceTrustService` privilege | App attempts to mark itself or another source trusted | Denied; trust changes only via the Settings UI flow |
| **SEC-18** | No root available | Attempt `su`, root helper discovery, and root-requiring operations on a `user` build | Nothing available |

SEC-16 deserves emphasis: a gate that has never been observed to fail is a gate
nobody has shown to work.

## 5. Device tests — DEV

Phase 10+ on physical hardware. Cuttlefish covers a subset.

| ID | Area | Test |
| --- | --- | --- |
| DEV-01 | Display | Resolution, density, refresh rate, rotation, brightness |
| DEV-02 | Touch | Multi-touch, gestures, palm rejection, stylus where present |
| DEV-03 | Wi-Fi | Scan, connect (WPA2/WPA3), throughput, roaming |
| DEV-04 | Bluetooth | Pair, audio profile, BLE, multi-device |
| DEV-05 | Camera | Photo, video, front/rear, flash, Camera2/CameraX |
| DEV-06 | Audio | Playback, capture, routing, volume, headset, call audio |
| DEV-07 | USB | Charging, MTP, accessory, ADB (where enabled) |
| DEV-08 | Battery | Charge, discharge curve, idle drain, thermal behavior |
| DEV-09 | Sensors | Accelerometer, gyroscope, magnetometer, proximity, light |
| DEV-10 | GPS | Cold and warm fix, accuracy, indoor fallback |
| DEV-11 | Cellular | Registration, voice, SMS, data, VoLTE, roaming |
| DEV-12 | NFC | Tag read/write, HCE, P2P where supported |
| DEV-13 | Biometrics | Enrollment, unlock, Keystore auth binding, lockout |
| DEV-14 | Fold/unfold | State preserved across the hinge transition (foldables) |
| DEV-15 | External display | Multi-display, freeform windows |

## 6. Compatibility tests — CTS

| Gate | Requirement |
| --- | --- |
| CTS baseline | Run against **unmodified AOSP** in Phase 1. This is the reference — "CTS passes" is meaningless without knowing what the baseline passed. |
| CTS regression | Every FreeDroid build compared against the baseline. Any new failure is a release blocker. |
| CTS Verifier | Manual hardware tests, Phase 10+. |
| Real-app corpus | Install, launch, permission, update, uninstall on a fixed APK set. |
| NDK ABI | Native sample apps verifying ABI stability. |

## 7. Test execution matrix

| Test class | Every commit | Nightly | Pre-release | Phase gate |
| --- | --- | --- | --- | --- |
| BLD | ✅ (incremental) | ✅ (clean) | ✅ | ✅ |
| APP | subset | ✅ | ✅ | ✅ |
| **SEC** | **✅ (always)** | ✅ | ✅ | ✅ |
| DEV | — | — | ✅ | ✅ (10+) |
| CTS | — | ✅ | ✅ | ✅ |

Security tests run on **every** build. They are the cheapest place to catch a
regression that would otherwise ship, and they are the tests most likely to be
skipped under schedule pressure — which is why they are listed as unconditional.

## 8. Release blockers

A release does not ship if any of these hold:

- Any SEC test fails
- CTS regression versus baseline
- APP-11 (different-signature update) does not reject
- The release gate (SEC-16) fails, or has never been shown to fail on a bad image
- Any SELinux domain is permissive
- Any debug setting present in a `user` image
- Any test-key signature in a release artifact
- An unexplained SELinux denial in normal operation

## 9. Reporting

Test results are recorded per build with the build ID, the manifest SHA record
(`repo manifest -r`), and the variant. Results are archived with release
artifacts.

The verification table in `SECURITY_MODEL.md` is updated from actual results —
never in advance of them. A row moves from ⬜ to ✅ when a test has run and
passed, not when the feature has been implemented.
