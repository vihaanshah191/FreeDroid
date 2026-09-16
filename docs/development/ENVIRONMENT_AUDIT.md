# FreeDroid — Phase 0 Environment Audit

**Date:** 2026-09-16
**Branch:** `claude/freedroid-architecture-baseline-m9rn3u`
**Scope:** Read-only inspection. No source downloaded, no packages installed, no files deleted or modified outside this document.

---

## 1. Environment Report

### Host

| Property | Value |
| --- | --- |
| OS | Ubuntu 24.04.4 LTS (Noble Numbat) |
| Kernel | Linux 6.18.44-fc-v33 |
| Hostname | `vm` |
| Platform | KVM guest (full virtualization), ephemeral container |

### CPU

| Property | Value |
| --- | --- |
| Architecture | x86_64 |
| Model | Intel Xeon @ 2.10 GHz (family 6, model 207) |
| Logical CPUs | 4 (4 cores × 1 thread, 1 socket) |
| Notable ISA | AVX-512 (F/DQ/CD/BW/VL/VNNI/BF16/FP16), AMX, SHA-NI, AES-NI |
| L3 cache | 260 MiB (shared) |
| **Virtualization flags** | **none — no `vmx`, no `svm`** |

### Memory

| Property | Value |
| --- | --- |
| Total RAM | 16,482,220 kB ≈ **15.7 GiB** |
| Available | ≈ 15.2 GiB |
| **Swap** | **0 B — no swap configured** |
| `/dev/shm` | 16 GiB tmpfs |

### Disk

| Mount | Filesystem | Size | Used | **Available** |
| --- | --- | --- | --- | --- |
| `/` (incl. `/home/user`, `/tmp`) | `/dev/vda` ext4 | 252 GiB | 7.1 GiB | **30.0 GiB** |
| `/opt/claude-code` | `/dev/vdc` | 244 MiB | 217 MiB | 23 MiB |
| `/dev/shm` | tmpfs | 16 GiB | 0 | 16 GiB |

> The `252 GiB` figure is the device size, not the entitlement. `statvfs` reports **30.0 GiB available to non-root writers**, and the environment documents a fixed per-session writable allowance. **30 GiB is the real number.** Inode pressure is not a concern (16.6M free).

### Toolchain

| Tool | Status | Version / Path |
| --- | --- | --- |
| Java (JDK) | ✅ present | OpenJDK **21.0.10**, `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` |
| Python | ✅ present | **3.11.15** (`python3` and `python` both resolve) |
| Git | ✅ present | **2.43.0** |
| `repo` | ❌ **MISSING** | not on `PATH` |
| Android SDK | ❌ **MISSING** | `ANDROID_HOME`/`ANDROID_SDK_ROOT` unset; no SDK directory anywhere |
| `adb` / `fastboot` | ❌ MISSING | — |
| `sdkmanager` / `avdmanager` | ❌ MISSING | — |
| Android Emulator | ❌ **MISSING** | binary absent **and** `/dev/kvm` absent |
| QEMU | ❌ MISSING | `qemu-system-x86_64` not installed |
| Docker | ⚠️ **client only** | client 29.3.1; **daemon not running, `/var/run/docker.sock` does not exist** |
| Podman | ❌ MISSING | — |
| Gradle | ✅ present | 8.14.3 (`/opt/gradle`) |
| Maven | ✅ present | 3.9.11 (`/opt/maven`) |
| Node | ✅ present | 20 / 21 / 22 under `/opt` |
| curl / wget | ✅ present | curl 8.5.0, wget 1.21.4 |

### Privileges and limits

- Running as `uid=0(root)`; `sudo` and `apt-get` both available → package installation is technically possible.
- `ulimit -n` = 20000 (AOSP wants ≥ 1024; satisfied).
- `ulimit -u` = 64318.

### Network

Outbound HTTPS is proxied (`HTTPS_PROXY`, CA bundle at `/root/.ccr/ca-bundle.crt`, git config injection enabled). Reachability verified read-only:

| Endpoint | Result |
| --- | --- |
| `https://android.googlesource.com/` | HTTP 200 ✅ |
| `https://gerrit.googlesource.com/git-repo` | HTTP 200 ✅ |
| `https://dl.google.com/android/repository/repository2-3.xml` | HTTP 200 ✅ |
| `https://source.android.com/` | HTTP 200 ✅ |
| `platform/manifest` ref enumeration | ✅ succeeded (branches and tags listed) |

AOSP sources are reachable. Bandwidth/throughput for a multi-hundred-GB sync was **not** measured.

---

## 2. Detected AOSP / Project State

| Check | Result |
| --- | --- |
| Existing AOSP checkout | ❌ **None.** No `.repo`, `build/envsetup.sh`, `build/soong`, `frameworks/base`, `system/core`, or `packages/apps` found under `/home`, `/opt`, `/srv`, `/data`, `/workspace`. |
| Existing project in CWD | ❌ **None.** `/home/user/FreeDroid` contained only `.git/` (124 KiB total) before this audit. |
| Git repository | ✅ Initialized, **zero commits**. `HEAD` points at unborn branch `claude/freedroid-architecture-baseline-m9rn3u`. |
| Working tree status | ✅ **Clean.** `git status --porcelain` empty. No stashes, no local branches, no tags. |
| Remote | `origin` → `https://github.com/vihaanshah191/FreeDroid` |
| Remote state | **Completely empty** — `git ls-remote --heads --tags origin` returned nothing. No branches, no tags, no history. |

**Conclusion: this is a greenfield repository. Nothing exists that could be destroyed. No permission to overwrite anything is required, because there is nothing to overwrite.**

---

## 3. Missing Dependencies

### Required for any AOSP build

| Package | Status |
| --- | --- |
| `repo` (git-repo launcher) | ❌ missing |
| `flex` | ❌ missing |
| `gperf` | ❌ missing |
| `rsync` | ❌ missing |
| `ccache` | ❌ missing |
| `lz4` | ❌ missing |
| `xsltproc` | ❌ missing |
| `libc6-dev-i386` | ❌ missing |
| `lib32z1-dev` | ❌ missing |
| `libgl1-mesa-dev` | ❌ missing |
| `libncurses5` | ❌ missing |
| `imagemagick` | ❌ missing |
| `xmlstarlet` | ❌ missing |

### Already satisfied

`build-essential`, `gcc` 13.3.0, `g++`, `make` 4.3, `cmake`, `ninja`, `bison`, `zip`, `unzip`, `bc`, `curl`, `openssl`, `zlib1g-dev`, `libx11-dev`, `x11proto-core-dev`, `libxml2-utils`, `fontconfig`, `gnupg`, `python3-pip`.

### Required for emulator / device work

| Component | Status |
| --- | --- |
| Android SDK platform-tools (`adb`, `fastboot`) | ❌ missing |
| Android SDK cmdline-tools (`sdkmanager`) | ❌ missing |
| Android Emulator | ❌ missing |
| **KVM (`/dev/kvm`)** | ❌ **absent, and CPU exposes no `vmx`/`svm` — not fixable by installing software** |
| Docker daemon | ❌ not running (client present, socket absent) |

> All missing *packages* are installable with `apt-get` (we have root). **KVM is not.** Nested virtualization is not exposed to this guest, so it cannot be enabled from inside it.

---

## 4. Estimated Disk / RAM Requirements vs. Available

Google's current published requirements for an AOSP platform build:

| Resource | AOSP requirement | This environment | Verdict |
| --- | --- | --- | --- |
| Disk — source checkout | ~250 GB (full history) | **30 GiB total** | ❌ **~8× short** |
| Disk — build output | ~150 GB | — | ❌ |
| Disk — **total** | **~400 GB** | **30 GiB** | ❌ **~13× short** |
| RAM | 64 GB recommended | **15.7 GiB, no swap** | ❌ ~4× short |
| CPU | 16–64 cores typical | **4 vCPU** | ⚠️ ~10–20 h full build if it fit |
| KVM for emulator/Cuttlefish | required | **absent** | ❌ hard blocker |

Even the most aggressive shallow strategy does not close the gap:

| Strategy | Approx. source size | Fits in 30 GiB? |
| --- | --- | --- |
| `repo sync` full history | ~300+ GB | ❌ |
| `repo init --depth=1 --partial-clone` + `repo sync -c --no-tags` | ~100–130 GB | ❌ |
| Single-branch, pruned, no prebuilt kernels | ~90 GB best case | ❌ |
| `frameworks/base` alone (~2 GB) | fits, **but is not independently buildable** — Soong needs the whole tree | ❌ |

There is no configuration of AOSP that builds in 30 GiB. This is arithmetic, not pessimism.

**Additional structural constraint:** this container is ephemeral and reclaimed after inactivity. Even if 400 GB were available, an AOSP checkout would be discarded between sessions. Anything that must survive has to be committed and pushed.

---

## 5. Recommended Development Configuration

### 5.1 Build host (must be provisioned separately)

| Resource | Minimum | Recommended |
| --- | --- | --- |
| CPU | 16 cores | 32–64 cores |
| RAM | 64 GB | 128 GB |
| Disk | 500 GB NVMe SSD | 1 TB NVMe SSD |
| Swap | 16 GB | 32 GB |
| `/dev/kvm` | **required** (emulator + Cuttlefish) | required |
| OS | Ubuntu 22.04 / 24.04 LTS | Ubuntu 24.04 LTS |
| ccache | 50 GB | 100 GB |

### 5.2 What this session's environment *is* suited for

This container is a capable **design, documentation, and component-development** environment:

- ✅ Architecture and security documentation (Phases 0, 9 design work)
- ✅ Gradle/Maven-based standalone APK development — the FreeDroid Launcher, Store client, and Updater client can be developed and unit-tested here as ordinary Android apps before being folded into the platform tree
- ✅ Manifest, device-overlay, SELinux policy, and build-config authoring (text artifacts, reviewed here, built on the big host)
- ✅ CI definitions, signing *procedure* documentation (never keys), test plans
- ❌ Not suited for: `repo sync`, `m`/`soong` builds, emulator boot, Cuttlefish, image generation

### 5.3 Repository structure — a correction to the proposed layout

The brief proposes `freedroid/aosp/` inside this repository. **I recommend against vendoring AOSP into this git repo**, and the brief explicitly invites this check ("Do not assume this exact structure is technically optimal until the AOSP build architecture … has been inspected"). Reasons:

1. AOSP is not one git repo — it is ~1,000 repos orchestrated by `repo` against a manifest. Flattening it into one git repo destroys `repo` tooling, per-project upstream tracking, and the ability to `repo sync` a new Android release.
2. It makes Requirement 16 (maintainable across future Android versions) effectively impossible — upstream rebases become manual merges across a million files.
3. It is a licensing and repo-hygiene problem, and it would put hundreds of GB into a repo that must be cloned by every contributor.

**Recommended structure** — AOSP stays upstream; FreeDroid is an *overlay* pulled in by a local manifest:

```text
FreeDroid/                       # this repository — overlay only, no AOSP source
├── manifests/
│   └── freedroid.xml            # local_manifest: pins AOSP tag + adds FreeDroid projects
├── freedroid/
│   ├── launcher/                # FreeDroidLauncher (Soong + Gradle dual-buildable)
│   ├── systemui/                # SystemUI overlays/extensions, not a fork
│   ├── settings/                # Settings overlays + FreeDroid settings injection
│   ├── framework/               # isolated framework extensions
│   ├── services/                # FreeDroid system services
│   ├── apps/                    # Store client, first-party apps
│   └── updater/                 # OTA client config, update_engine integration
├── device/freedroid/            # device trees (start: Cuttlefish / emulator target)
├── vendor/freedroid/            # product makefiles, RROs, branding, SELinux policy
├── docs/
│   ├── architecture/  security/  compatibility/  development/  roadmap/
└── scripts/                     # sync, build, verify, test helpers
```

The build host then does:

```text
mkdir aosp-workspace && cd aosp-workspace
repo init -u https://android.googlesource.com/platform/manifest -b <pinned tag>
git clone <FreeDroid> .repo/local_manifests/freedroid   # overlay comes in here
repo sync
```

`aosp-workspace/` is a *scratch directory on the build host*, never a tracked path. This keeps upstream Android and FreeDroid modifications cleanly separated — which is exactly the separation the brief asks for, achieved the way AOSP is designed to achieve it.

### 5.4 Customization mechanism preference

To satisfy "smallest reasonable change" and Requirement 16, prefer, in this order:

1. **RRO / Runtime Resource Overlays** (`vendor/freedroid/overlay/`) — branding, colors, config flags. Zero forked source.
2. **Product config + `PRODUCT_PACKAGES`** — swapping in FreeDroidLauncher instead of Launcher3.
3. **New standalone modules** — FreeDroid Store, Updater, FreeDroid system services.
4. **SELinux policy additions** in `vendor/freedroid/sepolicy/` — additive, never `permissive`.
5. **Patch files against upstream** (`patches/` with a documented apply order) — last resort, each one justified and tracked.
6. **Forking an AOSP component** — only with written justification of compatibility and security impact.

---

## 6. Recommended AOSP Release Strategy

### Available upstream (verified by live query, 2026-09-16)

Release branches present include `android14-*`, `android15-platform/qpr1/qpr2/security`, `android16-qpr1/qpr2/s1/s2/security`, and `android17-security-release`. Newest platform release **tags**: `android-15.0.0_r36`, `android-16.0.0_r1` … `android-16.0.0_r4`, `android-17.0.0_r1`.

### Recommendation: pin the baseline to `android-16.0.0_r4`

| Criterion | Rationale |
| --- | --- |
| Maturity | Android 16 has shipped QPR1 and QPR2 plus four platform release tags — the platform APIs and CTS surface have settled. |
| Security cadence | `android16-security-release` is active, giving a dedicated stream for security-only merges. This is the mechanism that satisfies **Requirement 15** (security updates deployable independently of OS releases). |
| Large-screen / adaptive | Android 16 carries the mature `WindowSizeClass`, activity-embedding, and adaptive-layout APIs needed for **Requirement 6** (one codebase, phone + tablet). |
| Cuttlefish support | Well-supported reference virtual device — the correct Phase 2 target. |
| `android-17.0.0_r1` rejected as baseline | A `.0_r1` tag is the first cut of a new major release. Building a *security-first* OS on an unproven initial tag trades a known-good foundation for freshness we do not need yet. Android 17 should be tracked for a planned future rebase, not adopted now. |

### Branch and merge policy

- **Pin to tags, never track a moving branch.** A moving branch means the baseline shifts under us and builds are not reproducible. Record the exact tag in `manifests/freedroid.xml`.
- **Two upstream streams:**
  - `android-16.0.0_rN` platform tags → FreeDroid *feature* releases (planned, tested, full regression pass).
  - `android16-security-release` → FreeDroid *security-only* releases (fast path, minimal diff, independently shippable). This is the concrete implementation of Requirement 15.
- **Rebase discipline:** keep the FreeDroid delta small enough that moving to Android 17 is a merge, not a rewrite. Every patch against upstream source is a maintenance liability and needs justification (Requirement 16).

### First device target: Cuttlefish (`aosp_cf_x86_64_phone`)

Recommended over the goldfish emulator (`aosp_x86_64`) for Phase 2 because Cuttlefish is Google's reference virtual device, supports a full AVB/Verified Boot chain, supports A/B updates and `update_engine` (Phase 8), and has a matching `aosp_cf_x86_64_tablet` target for Phase 11 — so phone and tablet validation share one codebase, as Requirement 6 demands. **It requires `/dev/kvm`.**

---

## 7. Risks

| # | Risk | Severity | Notes / Mitigation |
| --- | --- | --- | --- |
| R1 | **Environment cannot build AOSP** (30 GiB vs ~400 GB) | 🔴 Critical | Provision a dedicated build host. No workaround exists in-container. |
| R2 | **No KVM** — emulator and Cuttlefish cannot run | 🔴 Critical | Phases 2, 3, and all runtime security testing are blocked here. Needs a host with nested virt. |
| R3 | **Ephemeral container** — uncommitted work is destroyed | 🟠 High | Commit and push every meaningful increment. Never treat local state as durable. |
| R4 | 16 GiB RAM with **no swap** | 🟠 High | Even on adequate disk, AOSP link/javac steps would OOM-kill rather than swap. Build host must have ≥64 GB. |
| R5 | 4 vCPU → 10–20 h full builds | 🟡 Medium | Kills iteration speed. Mitigate with ≥16 cores and ccache on the build host. |
| R6 | **Signing key management** | 🔴 Critical | Release keys must never enter this repo. Requires an HSM or an offline signing host, documented before Phase 8. Test keys must be unmistakably distinct from release keys. |
| R7 | **Dev/prod config leakage** — `ro.adb.secure`, `userdebug` defaults, permissive SELinux domains escaping into production | 🔴 Critical | Enforce separate `eng`/`userdebug`/`user` product configs plus an automated pre-release gate that fails the build on debug settings in a `user` build. Requirements 10 and 14. |
| R8 | **Verified Boot / rollback need real hardware** | 🟠 High | Cuttlefish validates the AVB chain logically; hardware-backed Keystore, RPMB, and true rollback protection need a reference device (Phase 10). Must not be claimed as "working" before then. |
| R9 | Third-party store support widening attack surface | 🟠 High | Must be built on Android's existing `PackageInstaller` + `INSTALL_PACKAGES`/`REQUEST_INSTALL_PACKAGES` model. **No universal privileged install API.** Per-source user trust, honest warnings, no safety guarantees claimed. |
| R10 | Upstream divergence over time | 🟡 Medium | Enforce the overlay-first hierarchy in §5.4; audit the patch set every release. |
| R11 | Google-free configuration breaks apps depending on Play Services | 🟡 Medium | Expected and by design. Must be *documented* per Requirement 3 of the Google Services section, not papered over. Do not bundle proprietary Google components without rights. |
| R12 | Sync bandwidth/time through the proxy is unmeasured | 🟡 Medium | A ~100 GB sync could take many hours or hit relay limits. Measure on the build host before committing to a schedule. |
| R13 | CTS/CDD compatibility drift | 🟡 Medium | Requirement 1 (app compatibility) needs real CTS runs, not assumptions. Budget CTS infrastructure from Phase 3 onward. |

---

## 8. Blockers

**Hard blockers — Phase 1 (AOSP baseline) cannot start in this environment:**

1. **Disk: 30 GiB available vs ~400 GB required.** Not solvable by shallow clone, partial clone, or pruning. Off by more than an order of magnitude.
2. **RAM: 15.7 GiB with zero swap vs 64 GB recommended.**
3. **No `/dev/kvm` and no `vmx`/`svm` CPU flags.** Blocks Phase 2 (emulator boot) and Phase 3 (app compatibility testing) outright. Cannot be fixed from inside the guest.
4. **Container is ephemeral.** A multi-hundred-GB checkout would not survive between sessions even if it fit.

**Soft blockers — fixable here on approval:**

5. `repo` not installed (small; needs approval per the no-large-installs rule).
6. AOSP build dependencies missing (`flex`, `gperf`, `rsync`, `ccache`, `lz4`, `xsltproc`, `libc6-dev-i386`, `lib32z1-dev`, `libgl1-mesa-dev`, `libncurses5`, `imagemagick`, `xmlstarlet`) — ~200 MB via `apt-get`.
7. Android SDK platform-tools / cmdline-tools absent (~150 MB) — useful for APK-level work even without an emulator.
8. Docker daemon not running — container-based builds unavailable.

**Decision required from you:** items 1–4 are not engineering problems, they are provisioning problems. Phases 1–3 need a different machine. Everything else in the roadmap can start now.

---

## 9. Exact Next Steps

Ordered, each with an explicit approval gate. **Nothing below has been executed.**

### Immediate — no new dependencies, no risk

1. **Commit this audit** as the Phase 0 baseline record. *(Done with this commit.)*
2. **Decide the build-host question (blocks Phases 1–3).** Choose one:
   - **(a)** Provision a dedicated build host per §5.1 (≥16 cores, ≥64 GB RAM, ≥500 GB SSD, `/dev/kvm`) — *recommended*;
   - **(b)** Use a large-runner CI service for builds and accept slower iteration;
   - **(c)** Defer Phases 1–3 and proceed with design/docs/component work here first.
   Options (a) and (c) are complementary and can run in parallel.
3. **Approve the repository structure** in §5.3 — specifically the recommendation *not* to vendor AOSP in-tree. This decision shapes everything after it, so I want it explicit before writing any manifest.
4. **Author the Phase-0 documentation set** here (no build required):
   - `docs/architecture/OVERVIEW.md` — layering, what FreeDroid adds, what it must not touch
   - `docs/security/SECURITY_MODEL.md` — all ten threat classes from the brief, each with mitigation *and* honest remaining limitations
   - `docs/compatibility/ANDROID_COMPATIBILITY.md` — the compatibility surface that must not break, and the review gate for anything that might
   - `docs/development/BUILD.md`, `DEVICE_SUPPORT.md`, `TESTING.md`
   - `docs/roadmap/ROADMAP.md` — phases 0–12 with the gates between them

### On approval — small installs in this container

5. Install `repo` (~1 MB) and the missing AOSP build dependencies (~200 MB). Enables manifest authoring and syntax validation even though a full sync cannot run.
6. Install Android SDK cmdline-tools + platform-tools (~150 MB). Enables `apksigner`/`aapt2` work and Gradle-based development of the FreeDroid Launcher and Store client.

### On build host availability — Phase 1

7. `repo init` against `platform/manifest` pinned to **`android-16.0.0_r4`**; `repo sync`; record exact manifest SHAs for reproducibility.
8. Build **unmodified** `aosp_cf_x86_64_phone-userdebug`. Change nothing. The point is a known-good baseline and a measured build time.
9. Phase 2: boot the unmodified image under Cuttlefish. Capture boot logs, confirm `getenforce` → `Enforcing`, and record the baseline.
10. Phase 3: app-compatibility verification — install, launch, permissions, sandbox isolation (verify actual UID/SELinux separation, not config strings), update, uninstall, multiple install sources.

Only after 8–10 pass unmodified does Phase 4 (branding) begin. Per the brief: no phase skipping, and no security feature is reported as working until its behavior has been tested — a configuration file containing the right value is not evidence.

---

## 10. Confirmation of Non-Destructive Operation

- No files deleted, overwritten, or reset.
- No git history rewritten; no branches or tags deleted. The repository had no commits and no remote refs at audit time.
- No packages installed; no AOSP source downloaded.
- No signing keys generated; no bootloader touched; no device flashed.
- Network access limited to read-only reachability checks and git ref enumeration.
- Sole change to the working tree: creation of this document.
