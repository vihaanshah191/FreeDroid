# `vendor/` — FreeDroid vendor tree

Product configuration, resource overlays, branding assets, and SELinux policy.

| Path | Contents | Phase | Status |
| --- | --- | --- | --- |
| `freedroid/overlay/` | Runtime Resource Overlays — branding, colors, strings, config | 4 | **Empty placeholder** |
| `freedroid/sepolicy/` | Additive SELinux policy for FreeDroid services | 6 | **Empty placeholder** |

## `overlay/` — the preferred customization mechanism

RROs change resource values without modifying AOSP source. They are first in the
customization hierarchy (ADR-0001) because they cost nothing at upstream rebase
time. Most branding and configuration belongs here.

## `sepolicy/` — additive only

- New domains for new FreeDroid services. **Never `permissive`.**
- Never weakens an existing AOSP domain.
- `neverallow` rules must continue to hold; violations fail the policy build.
- Target: zero unexpected denials in normal operation (test SEC-15).

## Never in this tree

- **Signing keys of any kind.** Not release keys, not encrypted, not
  "temporarily". See [`SECURITY_MODEL.md`](../docs/security/SECURITY_MODEL.md) §9.
- Proprietary vendor blobs without documented redistribution rights.
- Proprietary Google components.
