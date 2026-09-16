# FreeDroid

An AOSP-derived operating system for phones and tablets, built on one principle:

> **Freedom of application installation without sacrificing security.**

Users choose where their apps come from. Every app still runs inside Android's
sandbox, under Android's permission model, with SELinux enforcing and Verified
Boot intact.

---

## Project status

**Phase 0 — environment audit complete. No AOSP baseline yet.**

See [`docs/roadmap/ROADMAP.md`](docs/roadmap/ROADMAP.md) for the phase plan and
[`docs/development/ENVIRONMENT_AUDIT.md`](docs/development/ENVIRONMENT_AUDIT.md)
for the current environment assessment, including the hard blockers that prevent
an AOSP build in the present development container.

Nothing in this repository has been built or booted. No security property
documented here has been empirically verified yet. Documents state clearly which
claims are *design intent* and which are *tested behavior*; at this stage
everything is design intent.

---

## What this repository is

This repository is the **FreeDroid overlay** — the delta between upstream AOSP
and FreeDroid. It contains manifests, device and product configuration, SELinux
policy additions, resource overlays, FreeDroid applications and services, and
documentation.

## What this repository is *not*

**It does not contain AOSP source.** AOSP is ~1,000 git repositories orchestrated
by the `repo` tool against a manifest. It is fetched from
`android.googlesource.com` at a pinned release tag and combined with this overlay
via a local manifest at build time.

The reasoning is recorded in
[`ADR-0001`](docs/architecture/decisions/ADR-0001-overlay-repository-structure.md).

---

## Baseline

| | |
| --- | --- |
| Upstream | AOSP, pinned to tag `android-16.0.0_r4` |
| Security stream | `android16-security-release` (separate, independently shippable merges) |
| First target | `aosp_cf_x86_64_phone` (Cuttlefish virtual device) |
| SELinux | Enforcing. Non-negotiable, in every build variant. |
| Verified Boot | AVB 2.0 with rollback protection, on hardware that supports it |
| Google Play Services | Not included. Not required. See [compatibility](docs/compatibility/ANDROID_COMPATIBILITY.md). |

---

## Documentation

| Document | Contents |
| --- | --- |
| [architecture/OVERVIEW.md](docs/architecture/OVERVIEW.md) | System layering, components, what FreeDroid adds and what it must not touch |
| [architecture/REPOSITORY_LAYOUT.md](docs/architecture/REPOSITORY_LAYOUT.md) | Overlay structure, customization hierarchy, where each kind of change belongs |
| [architecture/decisions/](docs/architecture/decisions/) | Architecture Decision Records |
| [security/SECURITY_MODEL.md](docs/security/SECURITY_MODEL.md) | Threat model — ten threat classes, mitigations, and honest limitations |
| [compatibility/ANDROID_COMPATIBILITY.md](docs/compatibility/ANDROID_COMPATIBILITY.md) | The compatibility surface that must not break, and the review gate for changes that might |
| [development/BUILD.md](docs/development/BUILD.md) | Build host requirements, sync, build, and image generation |
| [development/DEVICE_SUPPORT.md](docs/development/DEVICE_SUPPORT.md) | Device targets, adaptive phone/tablet strategy, porting requirements |
| [development/TESTING.md](docs/development/TESTING.md) | Build, application, security, and device test strategy |
| [development/ENVIRONMENT_AUDIT.md](docs/development/ENVIRONMENT_AUDIT.md) | Phase 0 environment audit |
| [roadmap/ROADMAP.md](docs/roadmap/ROADMAP.md) | Phases 0–12 with entry and exit gates |

---

## Non-negotiables

These are not preferences. A change that violates one of these does not merit
discussion about whether it is convenient.

- SELinux stays **enforcing**. No permissive domains in production.
- Verified Boot stays **enabled** in production.
- No application gets root.
- No universal privileged API that lets an ordinary app bypass the Android
  security model.
- No signing keys — of any kind — committed to this repository.
- No package signature validation bypass, ever, in any build variant.
- Development conveniences live in `eng`/`userdebug` only and are gated so they
  cannot reach a `user` build. This is enforced by an automated release gate, not
  by discipline.

## Licensing

AOSP is Apache 2.0 (with GPLv2 kernel components). FreeDroid contributions in
this repository are Apache 2.0 unless a file states otherwise. Proprietary
Google components are **not** included and must not be redistributed without the
appropriate rights.
