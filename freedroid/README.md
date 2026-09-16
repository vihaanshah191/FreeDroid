# `freedroid/` — FreeDroid components

Source for components FreeDroid adds to AOSP. **All directories here are empty
placeholders.** Each is populated by the roadmap phase named below.

| Directory | Component | Phase | Notes |
| --- | --- | --- | --- |
| `launcher/` | FreeDroidLauncher | 4 | Adaptive phone/tablet from the first commit |
| `systemui/` | SystemUI extensions | 5 | Overlay-first; not a SystemUI fork |
| `settings/` | Settings injection | 5 | Injection API; not a Settings fork |
| `framework/` | FreeDroid SDK | 6 | **Separate library — never merged into `framework.jar`** |
| `services/` | System services | 6 | Own SELinux domain each; no universal privileged API |
| `apps/store/` | FreeDroid Store | 7 | `REQUEST_INSTALL_PACKAGES` only — **never `INSTALL_PACKAGES`** |
| `updater/` | OTA client | 8 | `update_engine` integration |

## Build-tree placement

These do not build from this path. `repo` places them at their AOSP-tree
locations (e.g. `packages/apps/FreeDroidLauncher`) per
[`manifests/freedroid.xml`](../manifests/freedroid.xml). This directory is how
the FreeDroid repository organizes its own sources; the manifest maps them into
the build tree.

## Rules

- No AOSP source here. See [ADR-0001](../docs/architecture/decisions/ADR-0001-overlay-repository-structure.md).
- Prefer an RRO or product config over code. See the customization hierarchy in ADR-0001.
- Anything touching the protected surface in
  [`ANDROID_COMPATIBILITY.md`](../docs/compatibility/ANDROID_COMPATIBILITY.md) §2
  needs compatibility analysis before implementation.
