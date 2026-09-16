# FreeDroid

An AOSP-derived operating system for phones and tablets, built on one principle:

> **Freedom of application installation without sacrificing security.**

Users choose where their apps come from. Every app still runs inside Android's
sandbox, under Android's permission model, with SELinux enforcing and Verified
Boot intact.

---

## Project status — Phase 0

**Architecture and documentation only. Nothing has been built, booted, or tested.**

| | |
| --- | --- |
| AOSP source | **Not synced.** Not vendored here by design. |
| AOSP revision | **Not pinned.** `android-16.0.0_r4` is the *planned* baseline. |
| Builds | **None.** The current environment cannot build AOSP. |
| Security properties | **None verified.** Every claim is design intent. |
| Physical device | **None selected.** Deliberately deferred to Phase 10. |

The blocker is environmental, not architectural: the development container has
30 GiB of writable disk against AOSP's ~400 GB requirement, 15.7 GiB RAM against
64 GB, and no `/dev/kvm`. See
[`ENVIRONMENT_AUDIT.md`](docs/development/ENVIRONMENT_AUDIT.md).

**Next engineering step:** provision a persistent AOSP build host — 64+ GB RAM,
500+ GB SSD, 16+ cores, KVM enabled.

---

## What this repository is

The **FreeDroid overlay** — the delta between upstream AOSP and FreeDroid:
manifests, device and product configuration, SELinux policy, resource overlays,
FreeDroid applications and services, and documentation.

## What this repository is not

**It does not contain AOSP source.** AOSP is ~1,000 git repositories orchestrated
by `repo` against a manifest. It is fetched from `android.googlesource.com` at a
pinned release tag and composed with this overlay at sync time, on a build host.

Reasoning: [ADR-0001](docs/architecture/decisions/ADR-0001-overlay-repository-structure.md).

---

## Structure

```text
FreeDroid/
├── manifests/freedroid.xml     repo local manifest      [placeholder]
├── freedroid/
│   ├── launcher/               FreeDroidLauncher        [Phase 4]
│   ├── systemui/               SystemUI extensions      [Phase 5]
│   ├── settings/               Settings injection       [Phase 5]
│   ├── framework/              FreeDroid SDK            [Phase 6]
│   ├── services/               System services          [Phase 6]
│   ├── apps/store/             FreeDroid Store          [Phase 7]
│   └── updater/                OTA client               [Phase 8]
├── device/freedroid/           Device configuration     [Phase 4]
├── vendor/freedroid/
│   ├── overlay/                RROs, branding           [Phase 4]
│   └── sepolicy/               Additive SELinux policy  [Phase 6]
├── docs/                       Architecture and planning
└── scripts/                    Tooling
```

Component directories are empty placeholders. Each carries a `README.md` naming
the phase that fills it and the constraints that apply there.

---

## Two configurations

```text
FreeDroid Open  (default)          FreeDroid GMS  (aspirational)
├── AOSP                           ├── AOSP + FreeDroid components
├── FreeDroid components           ├── Google Play Store        [licensed]
├── FreeDroid Store                ├── Google Play Services     [licensed]
├── direct APK installation        └── other Google components  [licensed]
└── third-party app stores
                                   Requires CTS/CDD compliance and a Google
Fully functional. No Google        licensing agreement. NOT GUARANTEED.
dependency.                        No proprietary or unofficial Google
                                   components are included in this repository.
```

Milestone path: `Android compatibility → CTS/CDD compliance → GMS licensing/certification`.

---

## Documentation

| Document | Contents |
| --- | --- |
| [architecture/OVERVIEW.md](docs/architecture/OVERVIEW.md) | Purpose, AOSP relationship, layering (framework, ART, kernel, HAL), FreeDroid components, phone and tablet architecture, GMS |
| [architecture/REPOSITORY_LAYOUT.md](docs/architecture/REPOSITORY_LAYOUT.md) | Overlay structure, where each change belongs, manifest strategy |
| [architecture/decisions/](docs/architecture/decisions/) | Architecture Decision Records |
| [security/SECURITY_MODEL.md](docs/security/SECURITY_MODEL.md) | Every mechanism split four ways — what AOSP provides, what FreeDroid intends, what needs hardware, what is untested — plus ten threat classes |
| [compatibility/ANDROID_COMPATIBILITY.md](docs/compatibility/ANDROID_COMPATIBILITY.md) | The compatibility contract, risks from FreeDroid's own modifications, review gate |
| [development/BUILD.md](docs/development/BUILD.md) | Current container vs. required build host; dependencies; build and signing |
| [development/DEVICE_SUPPORT.md](docs/development/DEVICE_SUPPORT.md) | Cuttlefish targets, adaptive strategy, physical device requirements |
| [development/TESTING.md](docs/development/TESTING.md) | BLD / APP / SEC / DEV suites and release blockers |
| [development/ENVIRONMENT_AUDIT.md](docs/development/ENVIRONMENT_AUDIT.md) | Phase 0 environment audit |
| [roadmap/ROADMAP.md](docs/roadmap/ROADMAP.md) | Phases 0–12: objective, prerequisites, deliverables, tests, exit criteria |

---

## Validation

```bash
./scripts/validate-structure.sh
```

Checks structure, document presence, manifest integrity, absence of vendored
AOSP, absence of key material, branding, link integrity, test-ID consistency, and
that the security invariants are actually stated. Runs anywhere — no AOSP, no
build host, no network.

---

## Non-negotiables

Not preferences. A change violating one of these does not merit a discussion
about whether it is convenient.

- SELinux stays **enforcing** — in every build variant, including `eng`.
- Verified Boot stays **enabled** in production.
- **No application gets root.**
- **No universal privileged installation API.** One install path:
  `PackageInstaller` → `PackageManagerService`.
- **No app holds `INSTALL_PACKAGES`** on production builds — the FreeDroid Store
  included. If the first-party store cannot live within the model, the model is
  not real.
- **No signing keys in this repository**, in any form.
- **No package signature validation bypass**, ever, in any variant.
- Source trust changes **what the user is told**, never **what the system
  permits**.
- Development conveniences live in `eng`/`userdebug` only, enforced by an
  automated release gate rather than by discipline.

---

## Licensing

AOSP is Apache 2.0 (with GPLv2 kernel components). FreeDroid contributions here
are Apache 2.0 unless a file states otherwise. Proprietary Google components are
**not** included and must not be redistributed without the appropriate rights.
