# `device/` — Device configuration

Device trees and board configuration for FreeDroid targets.

| Path | Target | Phase | Status |
| --- | --- | --- | --- |
| `freedroid/` | Cuttlefish virtual phone + tablet | 4 | **Empty placeholder** |

## Planned initial targets

```text
aosp_cf_x86_64_phone     → freedroid_cf_x86_64_phone
aosp_cf_x86_64_tablet    → freedroid_cf_x86_64_tablet
```

Both derive from the same FreeDroid codebase. They differ in device
configuration, not in source. See
[`DEVICE_SUPPORT.md`](../docs/development/DEVICE_SUPPORT.md).

**No physical device has been selected.** Selection criteria are in
`DEVICE_SUPPORT.md` §6; the mandatory one is a bootloader that can be
**re-locked with a custom AVB key**.
