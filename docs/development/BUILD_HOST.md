# FreeDroid — AOSP Build Host

**Status:** setup automation written and preflight-tested. **No build host has
been provisioned.** Provisioning is a hardware/cloud decision, not something the
tooling can do for you.

---

## 1. What this document is for

Everything in FreeDroid up to now has been built in a development container that
**cannot build AOSP**: 4 vCPU, 15.7 GiB RAM, 30 GiB writable disk, no
`/dev/kvm`. That is roughly 13× short on disk and 4× short on RAM, and the KVM
gap cannot be closed from inside the guest at all.

Phases 1–3 need a real build host. This document says what to provision and how
to set it up.

| | Development container | AOSP build host |
| --- | --- | --- |
| Purpose | Docs, `:core`, launcher app, config authoring | `repo sync`, AOSP builds, Cuttlefish, OTA |
| Exists | ✅ this session | ❌ **must be provisioned** |
| CPU | 4 vCPU | 16 minimum, 32–64 recommended |
| RAM | 15.7 GiB | **64 GB minimum**, 128 GB recommended |
| Disk | 30 GiB | **500 GB NVMe SSD** minimum, 1 TB recommended |
| `/dev/kvm` | absent | **required** |
| Persistence | ephemeral | persistent |

---

## 2. Provisioning

### Hardware requirements

| Resource | Minimum | Recommended | Why |
| --- | --- | --- | --- |
| CPU | 16 cores | 32–64 cores | ~10–20 h full build on 4 cores; ~1–2 h on 32 |
| RAM | 64 GB | 128 GB | Soong link and javac steps are memory-hungry |
| Swap | 16 GB | 32 GB | **Without swap a memory spike kills the build outright** |
| Disk | 500 GB NVMe | 1 TB NVMe | ~150 GB source + ~150 GB `out/` + ccache + headroom |
| Filesystem | ext4/xfs/btrfs | ext4 | **Must be case-sensitive** |
| `/dev/kvm` | required | required | Cuttlefish and the emulator |
| OS | Ubuntu 22.04 LTS | **Ubuntu 24.04 LTS** | AOSP's supported host |

Spinning disks are a false economy: AOSP builds are I/O-bound in several phases
and an HDD can double wall-clock time.

### The one setting you cannot fix later

**Nested virtualisation must be enabled when the instance is created.** It is a
hypervisor-level setting; no amount of configuration inside the guest turns it
on. Without it there is no `/dev/kvm`, and Phases 2 onward are blocked exactly
as they are in the current container.

Verify it on the running instance **before** starting a 100 GB sync:

```bash
ls -l /dev/kvm && grep -cE '\b(vmx|svm)\b' /proc/cpuinfo
```

### Cloud instance guidance

Instance families change too quickly to name specific SKUs with confidence.
Select on the properties instead:

| Provider | What to look for | Nested virtualisation |
| --- | --- | --- |
| Google Cloud | 32+ vCPU, 128 GB, local SSD or pd-ssd | Supported on most Intel/AMD machine types; enable the nested-virt licence on the image |
| AWS | 32+ vCPU, 128 GB, gp3/io2 EBS or instance store | **Only bare-metal (`*.metal`) instances expose KVM** — this is the common trap |
| Azure | 32+ vCPU, 128 GB, Premium SSD | Supported on Dv3/Ev3 and later |
| Self-hosted | Any modern x86_64 with VT-x/AMD-V | Enable in firmware |

Confirm the provider's current nested-virtualisation support before committing —
on AWS in particular, a normal EC2 instance will not do.

### Cost note

A 32-vCPU/128 GB instance is expensive to leave running. AOSP builds are bursty:
long sync, long first build, then incremental work. Consider stopping the
instance between sessions and keeping the workspace on a persistent volume, or
using a dedicated machine if the project is long-lived.

---

## 3. Setup

Two scripts, both idempotent and both runnable from a clone of this repository.

### Preflight

```bash
git clone <freedroid-repo> && cd FreeDroid
./scripts/setup-build-host.sh --check-only
```

Checks OS, architecture, cores, RAM, swap, disk, filesystem case-sensitivity,
`/dev/kvm`, and upstream reachability. Installs nothing. Exit 1 if the host is
not AOSP-ready, with the specific reason for each failure.

Run this **before** provisioning anything large — it is the cheapest way to find
out that nested virtualisation is off.

### Install

```bash
sudo ./scripts/setup-build-host.sh --with-cuttlefish
```

| Step | What it does |
| --- | --- |
| 1–4 | Preflight. Refuses to proceed if the host cannot build AOSP. |
| 5 | AOSP build dependencies via apt |
| 6 | `repo` to `/usr/local/bin/repo` |
| 7 | Android SDK: `platforms;android-36`, `build-tools;35.0.0`, `cmdline-tools;latest` |
| 8 | ccache at 100 GB with compression |
| 9 | `nofile` limit → 65536; adds you to `kvm`, `cvdnetwork`, `render` |
| 10 | Cuttlefish host packages (`--with-cuttlefish`) |
| 11 | Creates the workspace directory |

Flags: `--check-only`, `--force` (install on an undersized host, without
claiming it is ready), `--skip-sdk`, `--with-cuttlefish`.

**The script refuses to install on a host that cannot build AOSP** unless you
pass `--force`, and even then it never reports the host as ready. There is no
mode in which it weakens a requirement to make the output look better.

### Shell profile

```bash
export ANDROID_HOME=/opt/android-sdk
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
export USE_CCACHE=1
export CCACHE_EXEC=/usr/bin/ccache
export CCACHE_DIR=/var/cache/ccache
```

Then **log out and back in** — group membership and the file-descriptor limit
only apply to a new session.

### Verify

```bash
./scripts/verify-build-host.sh
```

Re-derives everything from the live system rather than trusting that setup did
what it said. Read-only. Exit 0 only when the host is genuinely ready.

The two scripts are deliberately separate: "the setup script ran" and "this host
can build AOSP" are different claims, and only the second one matters.

---

## 4. Package names that have changed

Three names in widely-copied AOSP setup guides no longer exist on Ubuntu 24.04.
Verified against apt on noble:

| Guide says | Reality on 24.04 | Use instead |
| --- | --- | --- |
| `git-core` | transitional package, removed | `git` |
| `libncurses5` | removed | `libncurses-dev` + `libtinfo6` |
| `qemu-kvm` | renamed | `qemu-system-x86` |

A setup script carrying any of these fails partway through with an unhelpful apt
error. The full list in `setup-build-host.sh` was checked package-by-package
against apt before being committed.

---

## 5. Versions this project has proven

Everything below was exercised in the development container, so these are
measured, not assumed:

| Component | Version |
| --- | --- |
| Gradle | 8.14.3 |
| JDK | OpenJDK 21.0.10 (AOSP still uses its own prebuilt JDK) |
| Python | 3.11.15 |
| Git | 2.43.0 |
| Android Gradle Plugin | 8.11.1 |
| Kotlin | 2.0.21 |
| Compose BOM | 2025.06.00 |
| SDK platform | `android-36` |
| SDK build-tools | `35.0.0` |

**Planned but never synced:** AOSP `android-16.0.0_r4`. The tag was confirmed to
exist upstream by a read-only query; nothing has been fetched from it.

---

## 6. After setup — Phase 1

```bash
mkdir -p ~/aosp-workspace && cd ~/aosp-workspace

repo init -u https://android.googlesource.com/platform/manifest \
          -b android-16.0.0_r4 --partial-clone --clone-filter=blob:limit=10M

git clone <freedroid-repo> .repo/local_manifests/freedroid-src
ln -sf freedroid-src/manifests/freedroid.xml .repo/local_manifests/freedroid.xml

repo sync -c -j"$(nproc)" --no-tags --no-clone-bundle
repo manifest -r -o manifest-$(date +%Y%m%d).xml   # pin every project to a SHA
```

Expect several hours and 100+ GB on first sync. `repo sync` is resumable.

Then build an **unmodified** baseline — change nothing, the point is a known-good
reference:

```bash
source build/envsetup.sh
lunch aosp_cf_x86_64_phone-trunk_staging-userdebug
m -j"$(nproc)"
```

Full detail in [`BUILD.md`](BUILD.md). Phase gates in
[`../roadmap/ROADMAP.md`](../roadmap/ROADMAP.md).

---

## 7. What is still not solved

Provisioning a host unblocks Phases 1–3. It does **not** unblock:

- **Hardware-rooted security properties.** Verified Boot, rollback protection,
  hardware Keystore and real FBE need a physical device (Phase 10). Cuttlefish
  demonstrates policy and API behaviour, not hardware protection.
- **Real radio, sensors, camera, thermal behaviour.** Emulated or absent.
- **CTS on hardware.** Phase 10+.

A green Cuttlefish run is a necessary step, not a sufficient one, and the
verification table in
[`../security/SECURITY_MODEL.md`](../security/SECURITY_MODEL.md) Part IV marks
which rows can never be satisfied by an emulator.
