# FreeDroid — Device Support Strategy

**Phase:** 0 (Environment + Architecture)
**Status:** Design intent. **No device has been built for or booted.**
**Physical device selection:** **NONE. Deliberately deferred.**

---

## 1. Principle: one codebase, many form factors

FreeDroid maintains **one** operating system codebase. Phones and tablets differ
in device configuration and in runtime layout decisions — never in a separate
source tree, a separate application build, or a separate branch.

Two codebases diverge. Divergence means a security fix applied to one and
forgotten on the other. That is a security failure, not merely a maintenance
cost, and it is the reason this is a hard architectural rule rather than a
preference.

---

## 2. Initial validation targets

```text
aosp_cf_x86_64_phone       Cuttlefish virtual phone    — Phases 1-3 baseline
aosp_cf_x86_64_tablet      Cuttlefish virtual tablet   — Phase 9 validation
```

Later, once the FreeDroid product exists (Phase 4+):

```text
freedroid_cf_x86_64_phone
freedroid_cf_x86_64_tablet
```

Both derive from the same FreeDroid codebase and the same source. They differ in
device configuration only.

**Neither has been built. Neither has been booted.** Cuttlefish requires
`/dev/kvm`, which the current development container does not have.

---

## 3. Why Cuttlefish is the initial target

### 3.1 Versus the goldfish emulator (`aosp_x86_64`)

| Capability | Cuttlefish | Goldfish | Why it matters |
| --- | --- | --- | --- |
| Full AVB / Verified Boot chain | ✅ | ⚠️ limited | Phase 8 OTA and Phase 9 hardening depend on it |
| A/B (Virtual A/B) + `update_engine` | ✅ | ❌ | **Phase 8 is impossible without it** |
| dm-verity | ✅ | ⚠️ limited | System integrity testing |
| Matching tablet target | ✅ | ⚠️ | One codebase, both form factors |
| Reference status | Google's reference virtual device | Legacy SDK emulator | Tracks platform behavior closely |
| Multi-device / multi-display | ✅ | limited | Large-screen and external-display testing |

Phases 8 and 9 require the first three rows. Choosing Cuttlefish at Phase 1
avoids re-validating everything on a different target later.

### 3.2 Why a virtual device before physical hardware

1. **Reproducible.** Every developer and CI runner gets an identical device. A
   failure is reproducible rather than attributed to one person's handset.
2. **No hardware bring-up in the way.** Phases 1–9 test FreeDroid's own
   architecture. Debugging vendor blobs and kernel bring-up simultaneously would
   confound every result.
3. **No flashing risk.** No bricked devices, no bootloader mistakes.
4. **Parallelizable in CI.** Many instances at once.
5. **Phase 10 is then a port, not a bring-up plus a port.**

### 3.3 What Cuttlefish cannot demonstrate

Important, and easy to forget under schedule pressure:

| Property | Why not | Deferred to |
| --- | --- | --- |
| Hardware root of trust | No real bootloader, no fused keys | Phase 10 |
| Hardware-backed Keystore | Software-emulated KeyMint — **no hardware guarantee** | Phase 10 |
| Rollback protection | No RPMB or tamper-evident monotonic storage | Phase 10 |
| Real Verified Boot | No locked bootloader, no hardware key | Phase 10 |
| MTE / PAC / BTI | Not present on x86_64 | Phase 10 (arm64) |
| Real radio, sensors, camera, battery | Emulated or absent | Phase 10 |
| Thermal and power behavior | Not modelled | Phase 10 |

**A passing security test on Cuttlefish demonstrates correct policy and API
behavior. It does not demonstrate hardware protection.** The verification table
in [`SECURITY_MODEL.md`](../security/SECURITY_MODEL.md) Part IV marks which rows
are hardware-dependent, and those stay unverified until Phase 10.

---

## 4. Form-factor strategy

### 4.1 Mechanisms

| Mechanism | Purpose |
| --- | --- |
| Resource qualifiers — `sw600dp`, `sw720dp`, `w840dp`, `-land`, `-port`, `-night` | Layout and resource selection |
| `WindowSizeClass` (COMPACT / MEDIUM / EXPANDED) | Runtime layout decisions |
| `WindowMetricsCalculator` | Current **window** bounds, not display bounds |
| Activity embedding (`SplitController`) | Two-pane list/detail on large windows |
| `onConfigurationChanged` / state hoisting | Surviving size changes without losing state |
| `PRODUCT_CHARACTERISTICS` | Build-level config only where hardware genuinely differs |

### 4.2 The rule

**Prohibited:**

```kotlin
if (isTablet()) { showTwoPane() }                          // device category
if (resources.configuration.smallestScreenWidthDp >= 600)  // device proxy
if (display.width > 1200) { … }                            // display, not window
```

**Required:**

```kotlin
when (WindowSizeClass.compute(windowMetrics).windowWidthSizeClass) {
    WindowWidthSizeClass.COMPACT  -> showSinglePane()
    WindowWidthSizeClass.MEDIUM,
    WindowWidthSizeClass.EXPANDED -> showTwoPane()
}
```

The window is not the display. Split-screen on a tablet gives an app a compact
window; a foldable changes window size mid-session. Device-category branching is
wrong in both cases — a correctness requirement, not a style preference.

Android 16 ignores orientation and resizability restrictions on large screens for
apps targeting SDK 36. FreeDroid's apps must handle arbitrary window sizes, and
FreeDroid must not reintroduce restrictions AOSP removed.

### 4.3 Form-factor matrix

| Configuration | Width class | Expected behavior |
| --- | --- | --- |
| Phone portrait | COMPACT | Single pane, bottom navigation |
| Phone landscape | COMPACT/MEDIUM | Single pane, adjusted density |
| Phone split-screen | COMPACT | Single pane in a reduced window |
| Foldable closed | COMPACT | Single pane |
| Foldable open | MEDIUM/EXPANDED | Two pane; **state preserved across the fold** |
| Tablet portrait | MEDIUM/EXPANDED | Two pane, navigation rail |
| Tablet landscape | EXPANDED | Two or three pane, rail or drawer |
| Tablet split-screen | COMPACT/MEDIUM | Adapts to the given window |
| External display | EXPANDED | Multi-pane, freeform windows |

---

## 5. Hardware features

Features are declared, queried, and degraded gracefully — never assumed.

| Feature | Constant | Typical phone | Typical tablet |
| --- | --- | --- | --- |
| Telephony / SMS / cellular | `android.hardware.telephony` | ✅ | **often absent** |
| GPS | `android.hardware.location.gps` | ✅ | usually |
| NFC | `android.hardware.nfc` | usually | rarely |
| Camera | `android.hardware.camera.any` | ✅ | ✅ |
| Bluetooth | `android.hardware.bluetooth` | ✅ | ✅ |
| Wi-Fi | `android.hardware.wifi` | ✅ | ✅ |
| Fingerprint | `android.hardware.fingerprint` | usually | varies |
| StrongBox | `android.hardware.strongbox_keystore` | varies | varies |

FreeDroid system apps must query `PackageManager.hasSystemFeature()` and degrade
cleanly. **A Wi-Fi-only tablet must not show a broken dialer.** Third-party apps
use `<uses-feature>` and are filtered by the platform as on any Android device.

---

## 6. Physical device support — requirements

> **No physical device has been selected, and none should be until Phase 10.**
>
> Selecting hardware early would bias architecture toward one SoC's quirks and
> commit the project to vendor blobs and kernel maintenance before any of the
> architecture has been validated. There is currently no technical reason strong
> enough to justify that.

When selection happens, these are the requirements.

### 6.1 Mandatory — a device failing any of these is disqualified

| # | Requirement | Why |
| --- | --- | --- |
| 1 | **Bootloader can be unlocked *and re-locked* with a custom AVB key** | Without re-locking, boot state stays ORANGE, the AVB chain is not rooted in our key, and Verified Boot is theatre. **This eliminates most commercial devices and must be verified first, not last.** |
| 2 | **Tamper-evident monotonic storage** (RPMB or equivalent) | Rollback protection is impossible without it |
| 3 | **Kernel sources available** | GPLv2 requires it — but availability and *usability* differ |
| 4 | **Vendor blobs available with redistribution rights** | Otherwise the build is not legally distributable |
| 5 | **Hardware-backed Keystore** (TEE minimum) | Encryption key derivation and rate limiting depend on it |

### 6.2 Required HAL and hardware support

Each must be working before a device is called supported:

| Area | Requirement |
| --- | --- |
| Bootloader | Unlock/re-lock, custom AVB key, fastboot |
| Kernel / device tree | Boots, GKI-compatible preferred |
| Display | Resolution, density, refresh rate, rotation, brightness |
| Touch | Multi-touch, gestures, palm rejection |
| GPU | Hardware acceleration, composition, `SurfaceFlinger` |
| Wi-Fi | Scan, WPA2/WPA3, throughput, roaming |
| Bluetooth | Pair, audio profiles, BLE |
| Camera | Camera2/CameraX, front and rear, video, flash |
| Audio | Playback, capture, routing, headset, call audio |
| Battery | Charge, discharge reporting, thermal management |
| Sensors | Accelerometer, gyroscope, magnetometer, proximity, light |
| GPS | Cold and warm fix, accuracy |
| Cellular | Registration, voice, SMS, data, VoLTE — where telephony is present |
| NFC | Tag read/write, HCE — where present |
| Keystore / Gatekeeper | Hardware-backed keys, attestation, rate limiting |

### 6.3 Strongly preferred

- Active vendor security patch support for the intended device lifetime
- GKI compatibility — sharply reduces kernel maintenance
- Virtual A/B partition support
- MTE, PAC, BTI in the SoC
- An existing AOSP or well-maintained community device tree

### 6.4 Disqualifying

- Bootloader that cannot be unlocked, or cannot be **re-locked with a custom key**
- Vendor blobs with no redistribution rights
- No kernel source
- SoC vendor abandoned with no security patches

---

## 7. Porting checklist

Per device, before it is called supported:

- [ ] Device tree under `device/freedroid/<device>/`
- [ ] Kernel builds and boots
- [ ] Vendor blobs extracted, licensing documented
- [ ] AVB configured with a FreeDroid key; **boot state verified GREEN when locked**
- [ ] Rollback index configured and tested with an **actual downgrade attempt**
- [ ] SELinux policy complete, **enforcing**, zero denials in normal operation
- [ ] FBE enabled and verified on a real userdata partition
- [ ] Virtual A/B working; OTA applies and rolls back on induced failure
- [ ] All DEV tests in [`TESTING.md`](TESTING.md) pass
- [ ] CTS run with no regression versus the AOSP baseline
- [ ] CTS Verifier manual tests pass
- [ ] `user` build passes the release gate

**A device is not "supported" until every box is checked.** Partial support is
documented as partial, with the specific gaps named. "Mostly works" is not a
support status a user can act on.

---

## 8. Roadmap

| Phase | Target | Form factor | Status |
| --- | --- | --- | --- |
| 1–3 | `aosp_cf_x86_64_phone` | Virtual phone | Planned — blocked on build host |
| 4–8 | `freedroid_cf_x86_64_phone` | Virtual phone | Planned |
| 9 | `freedroid_cf_x86_64_tablet` | Virtual tablet | Planned |
| 10 | Reference smartphone | Physical phone | **Not selected** |
| 11 | Reference tablet | Physical tablet | **Not selected** |
