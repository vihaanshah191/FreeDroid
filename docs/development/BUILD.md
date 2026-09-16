# FreeDroid — Build Environment and Procedure

**Phase:** 0 (Environment + Architecture)
**Status:** **No build has been performed.** No AOSP source has been synced.
**Planned baseline:** AOSP `android-16.0.0_r4` — *planned, not synced, not pinned*

---

## 1. Two environments — do not conflate them

FreeDroid development involves two distinct machines with different capabilities.
Most confusion about what is possible comes from treating them as one.

| | **Development container** (current) | **AOSP build host** (required, not provisioned) |
| --- | --- | --- |
| Purpose | Docs, architecture, app development, config authoring | `repo sync`, AOSP builds, Cuttlefish, images, OTA |
| Exists | ✅ Yes — this session | ❌ **No — must be provisioned** |
| CPU | 4 vCPU Intel Xeon @ 2.10 GHz | 16 cores minimum, 32–64 recommended |
| RAM | **15.7 GiB** | **64 GB minimum**, 128 GB recommended |
| Swap | **0 B — none** | 16 GB minimum, 32 GB recommended |
| Disk | **30 GiB writable** | **500 GB NVMe SSD** minimum, 1 TB recommended |
| `/dev/kvm` | ❌ **Absent** (no `vmx`/`svm` CPU flags) | ✅ **Required** |
| OS | Ubuntu 24.04.4 LTS | Ubuntu 22.04 or 24.04 LTS |
| Persistence | **Ephemeral** — reclaimed after inactivity | Persistent |
| Can build AOSP | ❌ **No** | ✅ Yes |

Full measurements: [`ENVIRONMENT_AUDIT.md`](ENVIRONMENT_AUDIT.md).

### 1.1 Why the current container cannot build AOSP

| Resource | AOSP needs | Container has | Shortfall |
| --- | --- | --- | --- |
| Disk (source + output) | ~400 GB | 30 GiB | **~13×** |
| RAM | 64 GB | 15.7 GiB | **~4×** |
| `/dev/kvm` | required | absent | **Not installable** |

This is arithmetic, not pessimism. No shallow-clone or partial-clone strategy
closes a 13× disk gap — the leanest viable single-branch sync is still ~90 GB.
`/dev/kvm` cannot be enabled from inside the guest because the CPU exposes no
virtualization extensions. And the container is ephemeral, so a multi-hundred-GB
checkout would not survive between sessions even if it fit.

**Do not attempt `repo sync` or an AOSP build in this container.**

### 1.2 What the current container *is* good for

- ✅ Architecture, security, and compatibility documentation
- ✅ Standalone Gradle Android app development (Launcher, Store, Updater clients)
- ✅ Manifest, product config, RRO, and SELinux policy authoring
- ✅ Repository scaffolding and structure validation
- ✅ CI definitions, test plans, signing *procedure* documentation (never keys)
- ❌ `repo sync`, `m`/Soong builds, Cuttlefish, image generation, OTA generation

---

## 2. Required AOSP build host

### 2.1 Hardware

| Resource | Minimum | Recommended | Why |
| --- | --- | --- | --- |
| **CPU** | 16 cores | 32–64 cores | ~10–20 h full build on 4 cores; ~1–2 h on 32 |
| **RAM** | 64 GB | 128 GB | Soong/ninja link and javac steps are memory-hungry |
| **Swap** | 16 GB | 32 GB | **Without swap, a memory shortfall is an OOM-kill, not a slowdown** |
| **Storage** | 500 GB NVMe SSD | 1 TB NVMe SSD | ~150 GB source + ~150 GB `out/` + ccache + headroom |
| **KVM** | `/dev/kvm` required | required | Cuttlefish and the emulator do not run without it |
| ccache | 50 GB | 100 GB | Substantially cuts incremental build time |
| Network | — | Fast, unmetered | First sync is ~100+ GB |

HDDs are not recommended. AOSP builds are I/O-bound in many phases and a spinning
disk can double wall-clock build time.

### 2.2 Operating system

| | |
| --- | --- |
| **Required** | Ubuntu 22.04 LTS or **Ubuntu 24.04 LTS** |
| Recommended | Ubuntu 24.04 LTS |
| Architecture | x86_64 |
| Not supported | macOS (case-insensitive FS by default), Windows, WSL for full builds |

### 2.3 KVM

Required for Cuttlefish and the Android Emulator. Verify:

```bash
ls -l /dev/kvm                       # must exist
grep -cE 'vmx|svm' /proc/cpuinfo     # must be non-zero
kvm-ok                               # from cpu-checker
```

On a cloud VM this means **nested virtualization must be enabled by the
provider** — it is not something the guest can turn on. Check before provisioning.

```bash
sudo usermod -aG kvm,cvdnetwork,render "$USER"   # then log out and back in
```

---

## 3. Build dependencies

### 3.1 Packages

```bash
sudo apt-get update
sudo apt-get install -y \
  git-core gnupg flex bison build-essential zip curl zlib1g-dev \
  libc6-dev-i386 libncurses5 lib32z1-dev libgl1-mesa-dev libxml2-utils \
  xsltproc unzip fontconfig rsync ccache lz4 gperf python3 python3-pip \
  openjdk-21-jdk imagemagick xmlstarlet
```

**Status in the current container** (from the audit):

| Present | Missing |
| --- | --- |
| `build-essential`, `gcc` 13.3.0, `g++`, `make`, `cmake`, `ninja`, `bison`, `zip`, `unzip`, `bc`, `curl`, `openssl`, `zlib1g-dev`, `libx11-dev`, `libxml2-utils`, `fontconfig`, `gnupg`, `python3-pip` | `flex`, `gperf`, `rsync`, `ccache`, `lz4`, `xsltproc`, `libc6-dev-i386`, `lib32z1-dev`, `libgl1-mesa-dev`, `libncurses5`, `imagemagick`, `xmlstarlet` |

### 3.2 Java

| | |
| --- | --- |
| Current container | OpenJDK **21.0.10** ✅ |
| Build host | OpenJDK 21 |

**AOSP builds with its own prebuilt JDK** from `prebuilts/jdk/`. The host JDK is
used only for auxiliary tooling and does not determine the build.

### 3.3 Python

| | |
| --- | --- |
| Current container | Python **3.11.15** ✅ |
| Build host | Python 3.10+ |

### 3.4 `repo`

**Status: not installed in the current container.**

```bash
mkdir -p ~/.local/bin
curl https://storage.googleapis.com/git-repo-downloads/repo > ~/.local/bin/repo
chmod a+rx ~/.local/bin/repo
export PATH="$HOME/.local/bin:$PATH"
repo --version
```

### 3.5 Android SDK and platform-tools

**Status: not installed in the current container.** Not required for an AOSP
platform build — AOSP builds its own `adb`, `aapt2`, and `apksigner` — but needed
for standalone Gradle app development and for device interaction.

```bash
# cmdline-tools (sdkmanager, avdmanager)
export ANDROID_HOME="$HOME/Android/Sdk"
mkdir -p "$ANDROID_HOME/cmdline-tools"
# download commandlinetools-linux-*.zip from developer.android.com, unzip to
# $ANDROID_HOME/cmdline-tools/latest

# platform-tools (adb, fastboot)
sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"
export PATH="$ANDROID_HOME/platform-tools:$PATH"
```

| Component | Purpose | Needed for |
| --- | --- | --- |
| platform-tools | `adb`, `fastboot` | Device interaction, testing, Cuttlefish |
| cmdline-tools | `sdkmanager`, `avdmanager` | SDK management |
| build-tools | `aapt2`, `apksigner`, `zipalign` | Standalone app development |
| platforms | Compile SDK | Gradle app builds |

### 3.6 ccache

```bash
export USE_CCACHE=1
export CCACHE_EXEC=/usr/bin/ccache
export CCACHE_DIR=/var/cache/ccache
ccache -M 100G
ccache -o compression=true
```

### 3.7 File descriptor limit

```bash
ulimit -n 65536     # AOSP needs well above the default 1024
```

---

## 4. Workspace layout

The workspace is **scratch space on the build host**, never version controlled.
See [ADR-0001](../architecture/decisions/ADR-0001-overlay-repository-structure.md).

```text
~/aosp-workspace/           # scratch — NOT in git
├── .repo/
│   ├── manifests/          # upstream AOSP manifest
│   └── local_manifests/
│       └── freedroid.xml   # ← from the FreeDroid repo
├── frameworks/ system/ …   # upstream AOSP
├── device/freedroid/       # ← FreeDroid repo, placed by repo sync
├── vendor/freedroid/       # ← FreeDroid repo, placed by repo sync
└── out/                    # build output, ~150 GB
```

---

## 5. Sync — Phase 1, build host only

> **TODO(phase-1):** Not yet executed. Commands below are untested against a
> FreeDroid tree.

```bash
mkdir -p ~/aosp-workspace && cd ~/aosp-workspace

# Pin to a release TAG, never a moving branch — a moving baseline means
# builds are not reproducible.
repo init -u https://android.googlesource.com/platform/manifest \
          -b android-16.0.0_r4 \
          --partial-clone --clone-filter=blob:limit=10M

# Add the FreeDroid overlay via the local manifest
git clone <freedroid-repo-url> .repo/local_manifests/freedroid-src
ln -sf freedroid-src/manifests/freedroid.xml .repo/local_manifests/freedroid.xml

repo sync -c -j"$(nproc)" --no-tags --no-clone-bundle
```

Expect **several hours and ~100+ GB** on first sync. `repo sync` is resumable.

**Record exact revisions for any build worth reproducing:**

```bash
repo manifest -r -o manifest-$(date +%Y%m%d).xml
```

This pins every project to a SHA. Archive it with the artifacts — a release that
cannot be rebuilt cannot be audited after the fact.

---

## 6. Build — Phase 1, build host only

```bash
cd ~/aosp-workspace
source build/envsetup.sh

# Phase 1: UNMODIFIED AOSP baseline. Change nothing.
lunch aosp_cf_x86_64_phone-trunk_staging-userdebug
m -j"$(nproc)"
```

Once the FreeDroid product exists (Phase 4+):

```bash
lunch freedroid_cf_x86_64_phone-trunk_staging-userdebug   # development
lunch freedroid_cf_x86_64_tablet-trunk_staging-userdebug  # tablet
lunch freedroid_cf_x86_64_phone-trunk_staging-user        # production candidate
```

### Variants

| Variant | Use | Constraints |
| --- | --- | --- |
| `eng` | Fast iteration | Test keys. **SELinux still enforcing.** |
| `userdebug` | Development, testing | Root via ADB. **Never distribute.** |
| `user` | Production | Debug off, ADB off, release keys, dm-verity on |

**SELinux is enforcing and package signature verification is on in all three.**
There is no permissive FreeDroid build.

### Incremental builds

```bash
m -j"$(nproc)"        # incremental
m installclean        # after a lunch target change
mm                    # current directory's module only
m clean               # full clean — rarely the right answer
```

Use `installclean` after switching targets, not `clean`. A full rebuild costs
hours and is almost never what the situation requires.

---

## 7. Output

```text
out/target/product/vsoc_x86_64/
├── system.img  vendor.img  product.img  system_ext.img
├── boot.img  vbmeta.img  super.img
└── obj/PACKAGING/target_files_intermediates/*.zip    # input to OTA generation
```

---

## 8. Run under Cuttlefish

**Requires `/dev/kvm`. Not possible in the current container.**

```bash
cd ~/aosp-workspace
launch_cvd --daemon
adb connect 0.0.0.0:6520
adb shell getenforce          # must print: Enforcing
stop_cvd
```

`getenforce` is the actual check. A makefile setting is not evidence.

---

## 9. Signing

### Development

AOSP test keys from `build/target/product/security/`. **These are publicly
known.** An image signed with them has no authenticity guarantee and must never
leave a development machine.

### Production

> **No production signing keys exist. None will be generated in this container.**

Release signing runs on an **HSM or air-gapped host**, never on the build host,
never with keys from this repository.

Requirements (see [`SECURITY_MODEL.md`](../security/SECURITY_MODEL.md) §9):

- Keys in an HSM or air-gapped host. **Never in Git. Never in CI variables.**
- Separate keys for platform, release/OTA, and APEX.
- Multi-party authorization for release signing.
- Per-operation audit logging including the artifact digest.
- A rotation plan written **and rehearsed** before it is needed.

---

## 10. Release gate

Every `user` build must pass `scripts/verify-build-variant.sh` (Phase 9). It fails
the build on:

- `ro.debuggable=1`
- `ro.adb.secure` not `1`
- Any permissive SELinux domain
- A test-key signature
- Any debug or test app in the image
- dm-verity disabled

A checklist is not a control. The gate is the control — and per SEC-16 the gate
itself must be tested against a deliberately bad image.

---

## 11. Troubleshooting discipline

When a build fails:

1. Capture the **exact** error, in full.
2. Determine the root cause.
3. Inspect the relevant source or configuration.
4. Make **one** targeted fix.
5. Rebuild.
6. Test.
7. Document the resolution.

Do not apply speculative fixes in sequence. Do not suppress or hide a failure.
**Never disable a security check to make a build or test pass** — a failing
security check is the finding, not an obstacle to it.

| Symptom | Likely cause |
| --- | --- |
| OOM during build | Insufficient RAM. Reduce `-j`, add swap, use a bigger host. |
| `No space left on device` | `out/` is large. `du -sh out/`. |
| `Too many open files` | `ulimit -n` too low. |
| `launch_cvd` fails | No `/dev/kvm`, or user not in the `kvm` group. |
| Sync interrupted | `repo sync` is resumable. Re-run it. |
