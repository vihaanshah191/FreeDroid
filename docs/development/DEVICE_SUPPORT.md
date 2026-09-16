# FreeDroid — Device Support

**Status:** Design intent. No device has been built for or booted.

---

## 1. Principle: one codebase, many form factors

FreeDroid maintains **one** operating system codebase. Phones and tablets differ
in device configuration and in runtime layout decisions — never in a separate
source tree, a separate build of the UI apps, or a separate branch.

Two codebases diverge. Divergence means a security fix applied to one and
forgotten on the other, which is a security failure and not merely a maintenance
cost.

## 2. Device target roadmap

| Phase | Target | Form factor | Status |
| --- | --- | --- | --- |
| 1–3 | `aosp_cf_x86_64_phone` (Cuttlefish) | Virtual phone | Planned |
| 4–9 | `freedroid_cf_x86_64` (Cuttlefish) | Virtual phone | Planned |
| 9 | `aosp_cf_x86_64_tablet` (Cuttlefish) | Virtual tablet | Planned |
| 10 | Reference smartphone (TBD) | Physical phone | Not selected |
| 11 | Reference tablet (TBD) | Physical tablet | Not selected |

### Why Cuttlefish rather than the goldfish emulator

| Capability | Cuttlefish | Goldfish (`aosp_x86_64`) |
| --- | --- | --- |
| AVB / Verified Boot chain | ✅ full | ⚠️ limited |
| A/B (Virtual A/B) + `update_engine` | ✅ | ❌ |
| dm-verity | ✅ | ⚠️ limited |
| Matching tablet target | ✅ | ⚠️ |
| Reference status | Google's reference virtual device | Legacy SDK emulator |

Phases 8 (OTA) and 9 (security hardening) need the first three rows. Choosing
Cuttlefish at Phase 1 avoids re-validating everything on a different target later.

**Cuttlefish requires `/dev/kvm`.** The current development container does not
have it. See [`ENVIRONMENT_AUDIT.md`](ENVIRONMENT_AUDIT.md).

## 3. Adaptive UI

### Mechanisms

| Mechanism | Use |
| --- | --- |
| Resource qualifiers — `sw600dp`, `sw720dp`, `w840dp`, `-land`, `-port`, `-night` | Layout and resource selection |
| `WindowSizeClass` (COMPACT / MEDIUM / EXPANDED) | Runtime layout decisions |
| `WindowMetricsCalculator` | Current window bounds — not display bounds |
| Activity embedding (`SplitController`) | Two-pane list/detail on large windows |
| `onConfigurationChanged` / state hoisting | Surviving size changes without losing state |
| `PRODUCT_CHARACTERISTICS` | Build-level config where hardware genuinely differs |

### Rules for FreeDroid UI code

**Prohibited:**

```kotlin
if (isTablet()) { showTwoPane() }                          // device category
if (resources.configuration.smallestScreenWidthDp >= 600)  // device proxy
if (display.width > 1200) { … }                            // display, not window
```

**Required:**

```kotlin
val widthClass = WindowSizeClass.compute(windowMetrics).windowWidthSizeClass
when (widthClass) {
    WindowWidthSizeClass.COMPACT  -> showSinglePane()
    WindowWidthSizeClass.MEDIUM,
    WindowWidthSizeClass.EXPANDED -> showTwoPane()
}
```

The window is not the display. In split-screen, a tablet gives an app a compact
window; on a foldable, the window changes size mid-session. Branching on device
category produces a layout that is wrong in both cases — this is a correctness
requirement, not a style preference.

Android 16 additionally ignores orientation and resizability restrictions on
large screens for apps targeting SDK 36. FreeDroid's apps must handle arbitrary
window sizes, and FreeDroid must not reintroduce restrictions AOSP removed.

### Form-factor matrix

| Configuration | Width class | Expected behavior |
| --- | --- | --- |
| Phone portrait | COMPACT | Single pane, bottom navigation |
| Phone landscape | COMPACT/MEDIUM | Single pane, adjusted density |
| Phone split-screen | COMPACT | Single pane in a reduced window |
| Foldable closed | COMPACT | Single pane |
| Foldable open | MEDIUM/EXPANDED | Two pane; state preserved across the fold |
| Tablet portrait | MEDIUM/EXPANDED | Two pane, navigation rail |
| Tablet landscape | EXPANDED | Two pane or three, rail or drawer |
| Tablet split-screen | COMPACT/MEDIUM | Adapts to the given window |
| Desktop / external display | EXPANDED | Multi-pane, freeform windows |

## 4. Hardware feature handling

Features are declared, queried, and degraded gracefully — never assumed.

| Feature | Constant | Typical phone | Typical tablet |
| --- | --- | --- | --- |
| Telephony | `android.hardware.telephony` | ✅ | varies (Wi-Fi-only tablets) |
| SMS | `android.hardware.telephony` | ✅ | varies |
| Cellular data | `android.hardware.telephony` | ✅ | varies |
| GPS | `android.hardware.location.gps` | ✅ | usually |
| NFC | `android.hardware.nfc` | usually | rarely |
| Camera | `android.hardware.camera.any` | ✅ | ✅ |
| Bluetooth | `android.hardware.bluetooth` | ✅ | ✅ |
| Wi-Fi | `android.hardware.wifi` | ✅ | ✅ |
| Fingerprint | `android.hardware.fingerprint` | usually | varies |
| StrongBox | `android.hardware.strongbox_keystore` | varies | varies |

FreeDroid system apps must query `PackageManager.hasSystemFeature()` and degrade
cleanly. A tablet without telephony must not show a broken dialer. Third-party
apps use `<uses-feature>` and are filtered by the platform as on any Android
device.

## 5. Reference hardware selection criteria

No device has been selected. When the time comes (Phase 10), these are the
criteria, in priority order:

### Mandatory

1. **Bootloader can be unlocked *and re-locked* with a custom AVB key.**
   Without re-locking, Verified Boot on the shipped device is theatre — the boot
   state stays ORANGE, the AVB chain is not rooted in our key, and §T5 of the
   security model does not hold. This criterion eliminates most devices and must
   be checked first, not last.
2. **Rollback index storage in tamper-evident hardware** — required for rollback
   protection.
3. **Vendor blobs available with redistribution rights** — or the device is not
   legally distributable.
4. **Kernel sources available** (GPLv2 requires this, but availability and
   *usability* differ).
5. **Hardware-backed Keystore** (TEE minimum, StrongBox preferred).

### Strongly preferred

6. Active vendor security patch support for the intended device lifetime.
7. Generic Kernel Image (GKI) compatibility — reduces kernel maintenance sharply.
8. Virtual A/B partition support.
9. MTE, PAC, BTI support in the SoC.
10. An existing AOSP or well-maintained community device tree.

### Disqualifying

- Bootloader that cannot be unlocked, or cannot be re-locked with a custom key.
- Vendor blobs with no redistribution rights.
- No kernel source.
- SoC vendor abandoned with no security patches.

## 6. Porting checklist

Per device, before it is considered supported:

- [ ] Device tree under `device/freedroid/<device>/`
- [ ] Kernel builds and boots
- [ ] Vendor blobs extracted, with documented licensing
- [ ] AVB configured with a FreeDroid key; boot state verified GREEN when locked
- [ ] Rollback index configured and tested with an actual downgrade attempt
- [ ] SELinux policy complete, **enforcing**, with zero denials in normal operation
- [ ] FBE enabled and verified on a real userdata partition
- [ ] A/B (Virtual A/B) working; OTA applies and rolls back on induced failure
- [ ] All device tests in [`TESTING.md`](TESTING.md) §5 pass
- [ ] CTS run with no regression versus the AOSP baseline
- [ ] CTS Verifier manual tests pass
- [ ] `user` build passes the release gate

**A device is not "supported" until every box is checked.** Partial support is
documented as partial, with the specific gaps named. "Mostly works" is not a
support status a user can act on.
