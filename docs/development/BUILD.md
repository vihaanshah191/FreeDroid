# FreeDroid — Build Guide

**Status:** Not yet executed. These procedures are written from AOSP's documented
build process and have **not** been validated on a FreeDroid tree. Expect
corrections after Phase 1.

---

## 1. Build host requirements

| Resource | Minimum | Recommended | Why |
| --- | --- | --- | --- |
| CPU | 16 cores | 32–64 cores | Full build is ~10–20 h on 4 cores, ~1–2 h on 32 |
| RAM | 64 GB | 128 GB | Soong/ninja link and javac steps are memory-hungry; **with no swap, shortfall means OOM-kill, not slowdown** |
| Swap | 16 GB | 32 GB | Survives spikes instead of failing the build |
| Disk | 500 GB NVMe SSD | 1 TB NVMe SSD | ~150 GB source + ~150 GB out + ccache + headroom |
| `/dev/kvm` | **required** | required | Cuttlefish and the emulator do not run without it |
| OS | Ubuntu 22.04 LTS | Ubuntu 24.04 LTS | AOSP's supported host |
| ccache | 50 GB | 100 GB | Cuts incremental build time substantially |

> **The current development container does not meet these requirements.** It has
> 4 vCPU, 15.7 GiB RAM, 30 GiB writable disk, and no `/dev/kvm`. See
> [`ENVIRONMENT_AUDIT.md`](ENVIRONMENT_AUDIT.md). Phases 1–3 require a
> separately provisioned host.

## 2. Host setup

```bash
sudo apt-get update
sudo apt-get install -y \
  git-core gnupg flex bison build-essential zip curl zlib1g-dev \
  libc6-dev-i386 libncurses5 lib32z1-dev libgl1-mesa-dev libxml2-utils \
  xsltproc unzip fontconfig rsync ccache lz4 gperf python3 python3-pip \
  openjdk-21-jdk imagemagick xmlstarlet
```

Note: AOSP builds with its **own prebuilt JDK** from `prebuilts/jdk/`. The host
JDK is used only for auxiliary tooling; its version does not determine the build.

### Install `repo`

```bash
mkdir -p ~/.local/bin
curl https://storage.googleapis.com/git-repo-downloads/repo > ~/.local/bin/repo
chmod a+rx ~/.local/bin/repo
export PATH="$HOME/.local/bin:$PATH"
```

### ccache

```bash
export USE_CCACHE=1
export CCACHE_EXEC=/usr/bin/ccache
export CCACHE_DIR=/var/cache/ccache
ccache -M 100G
ccache -o compression=true
```

### File descriptor limit

```bash
ulimit -n 65536   # AOSP needs well above the default 1024
```

## 3. Workspace layout

The workspace is **scratch space on the build host**. It is never version
controlled. See [ADR-0001](../architecture/decisions/ADR-0001-overlay-repository-structure.md).

```text
~/aosp-workspace/           # scratch — NOT in git
├── .repo/                  # repo metadata + local manifest
├── frameworks/ system/ …   # upstream AOSP
├── device/freedroid/       # ← FreeDroid repo, placed by repo sync
├── vendor/freedroid/       # ← FreeDroid repo, placed by repo sync
└── out/                    # build output
```

## 4. Sync

```bash
mkdir -p ~/aosp-workspace && cd ~/aosp-workspace

# Pin to the FreeDroid baseline tag. Do not track a moving branch:
# a moving baseline means builds are not reproducible.
repo init -u https://android.googlesource.com/platform/manifest \
          -b android-16.0.0_r4 \
          --partial-clone --clone-filter=blob:limit=10M

# Add the FreeDroid overlay via a local manifest
git clone https://github.com/vihaanshah191/FreeDroid .repo/local_manifests/freedroid-src
ln -sf freedroid-src/manifests/freedroid.xml .repo/local_manifests/freedroid.xml

repo sync -c -j"$(nproc)" --no-tags --no-clone-bundle
```

Expect **several hours and ~100+ GB** on first sync.

**Record the exact revisions for every build worth reproducing:**

```bash
repo manifest -r -o manifest-$(date +%Y%m%d).xml
```

This pins every project to a SHA. Archive it with the build artifacts. A release
that cannot be rebuilt cannot be audited after the fact.

## 5. Build

```bash
cd ~/aosp-workspace
source build/envsetup.sh

# Phase 1 baseline — unmodified AOSP, no FreeDroid product
lunch aosp_cf_x86_64_phone-trunk_staging-userdebug
m -j"$(nproc)"
```

Once the FreeDroid product exists (Phase 4+):

```bash
lunch freedroid_cf_x86_64-trunk_staging-userdebug   # development
lunch freedroid_cf_x86_64-trunk_staging-user        # production candidate
```

### Variants

| Variant | Use | Constraints |
| --- | --- | --- |
| `eng` | Fast iteration | Test keys. SELinux still **enforcing**. |
| `userdebug` | Development and testing | Root via ADB available. **Never distribute.** |
| `user` | Production | Debug off, ADB off, release keys, dm-verity on. |

SELinux is enforcing and package signature verification is on in **all three**.
There is no permissive FreeDroid build.

### Incremental builds

```bash
m -j"$(nproc)"              # incremental, respects dependencies
m installclean              # after a lunch target change
m clean                     # full clean; rarely necessary
mm                          # current directory's module only
```

`m installclean` after switching targets, not `m clean` — a full rebuild costs
hours and is almost never what the situation requires.

## 6. Output

```text
out/target/product/vsoc_x86_64/
├── system.img  vendor.img  product.img  system_ext.img
├── boot.img  vbmeta.img
├── super.img
└── obj/PACKAGING/target_files_intermediates/*.zip   # input to OTA generation
```

## 7. Run under Cuttlefish

**Requires `/dev/kvm`.**

```bash
sudo usermod -aG kvm,cvdnetwork,render "$USER"   # re-login afterwards
cd ~/aosp-workspace
launch_cvd --daemon
adb connect 0.0.0.0:6520
adb shell getenforce        # must print: Enforcing
stop_cvd
```

`getenforce` is the actual check. A `BOARD_SELINUX` setting in a makefile is not
evidence that SELinux is enforcing on a booted image.

## 8. Signing

### Development

Builds are signed with AOSP test keys from `build/target/product/security/`.
**These keys are publicly known.** An image signed with them provides no
authenticity guarantee whatsoever and must never leave a development machine.

### Production

Release signing happens on an **offline signing host or HSM**, never on the build
host, and never with keys stored in this repository.

```bash
# Conceptual. The real procedure runs on the signing host with HSM-backed keys.
sign_target_files_apks \
  --default_key_mappings /path/to/keys \
  signed-target-files.zip
```

Requirements, from [`SECURITY_MODEL.md`](../security/SECURITY_MODEL.md) §T10:

- Keys in an HSM or on an air-gapped host. Never in version control, never in CI.
- Separate keys for platform, release/OTA, and APEX.
- Multi-party authorization for release signing.
- Every signing operation logged with the artifact digest.
- A rotation plan written and rehearsed **before** it is needed.

## 9. OTA generation

```bash
ota_from_target_files \
  --output_metadata_path metadata.txt \
  signed-target-files.zip \
  freedroid-ota-<version>.zip

# Incremental (delta) update
ota_from_target_files \
  --incremental_from previous-signed-target-files.zip \
  signed-target-files.zip \
  freedroid-incremental-<from>-<to>.zip
```

Verification is on-device, against a key baked into the Verified-Boot-protected
system image. A package that fails verification is rejected regardless of where
it came from — including from our own servers.

## 10. Release gate

Every `user` build passes `scripts/verify-build-variant.sh` before release. It
fails the build on:

- `ro.debuggable=1`
- `ro.adb.secure` not `1`
- Any permissive SELinux domain
- A test-key signature
- Any debug or test app in the image
- dm-verity disabled

A checklist is not a control. This gate is the control, and it exists because
debug settings reach production when their removal depends on someone remembering.

## 11. Troubleshooting

Discipline when a build fails — from the project's coding rules:

1. Capture the **exact** error, in full.
2. Determine the root cause.
3. Inspect the relevant source or configuration.
4. Make one targeted fix.
5. Rebuild.
6. Test.
7. Document the resolution.

Do not apply speculative fixes in sequence. Do not suppress or hide a build
failure. Never disable a security check to make a build or test pass — if a
security check fails, that is the finding, not an obstacle to the finding.

| Symptom | Likely cause |
| --- | --- |
| OOM during build | Insufficient RAM. Reduce `-j`, add swap, or use a bigger host. |
| `No space left on device` | `out/` is large. Check with `du -sh out/`. |
| `Too many open files` | `ulimit -n` too low. |
| `launch_cvd` fails | No `/dev/kvm`, or user not in the `kvm` group. |
| Sync interrupted | `repo sync` is resumable. Re-run it. |
