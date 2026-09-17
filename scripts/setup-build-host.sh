#!/usr/bin/env bash
#
# FreeDroid — AOSP build host setup.
#
# Prepares a fresh Ubuntu 22.04 / 24.04 machine to sync and build AOSP, run
# Cuttlefish, and build the FreeDroid launcher. Idempotent: safe to re-run.
#
#   ./scripts/setup-build-host.sh --check-only   preflight only, install nothing
#   ./scripts/setup-build-host.sh                preflight, then install
#   ./scripts/setup-build-host.sh --force        install even if capacity is short
#   ./scripts/setup-build-host.sh --skip-sdk     skip the Android SDK
#   ./scripts/setup-build-host.sh --with-cuttlefish   also build/install cuttlefish host debs
#
# Exit codes:  0 ready  ·  1 preflight failed  ·  2 install failed  ·  3 bad usage
#
# WHAT THIS SCRIPT WILL NOT DO
#   It will not provision a machine, weaken a security setting, or pretend a
#   requirement is met. If the host is too small it says so and stops; --force
#   installs the toolchain anyway but never claims the host is AOSP-ready.
#
set -euo pipefail

# ---------------------------------------------------------------- requirements
# Google's published AOSP requirements, plus what this project measured.
readonly REQ_CORES_MIN=16
readonly REQ_CORES_REC=32
readonly REQ_RAM_GB_MIN=64
readonly REQ_RAM_GB_REC=128
readonly REQ_DISK_GB_MIN=400
readonly REQ_DISK_GB_REC=1000
readonly REQ_SWAP_GB_MIN=16

# Versions proven by this project. See docs/development/BUILD_HOST.md.
readonly SDK_PLATFORM="platforms;android-36"
readonly SDK_BUILD_TOOLS="build-tools;35.0.0"
readonly SDK_CMDLINE="cmdline-tools;latest"
readonly CMDLINE_TOOLS_ZIP="commandlinetools-linux-16111833_latest.zip"
readonly ANDROID_SDK_DIR="${ANDROID_SDK_DIR:-/opt/android-sdk}"
readonly AOSP_WORKSPACE="${AOSP_WORKSPACE:-$HOME/aosp-workspace}"
readonly CCACHE_SIZE="${CCACHE_SIZE:-100G}"

# Package names verified against Ubuntu 24.04 (noble). Three common AOSP guides
# still list names that no longer exist there:
#   git-core     -> transitional, gone; use git
#   libncurses5  -> gone; libncurses-dev + libtinfo6 cover the prebuilts
#   qemu-kvm     -> renamed qemu-system-x86
readonly -a PACKAGES=(
  git git-lfs gnupg flex bison build-essential zip unzip curl rsync
  zlib1g-dev libc6-dev-i386 libncurses-dev libtinfo6 lib32z1-dev
  libgl1-mesa-dev libxml2-utils xsltproc fontconfig ccache lz4 gperf
  python3 python3-pip openjdk-21-jdk imagemagick xmlstarlet bc libssl-dev
)
# Only needed for Cuttlefish / emulator work.
readonly -a KVM_PACKAGES=(qemu-system-x86 qemu-utils cpu-checker bridge-utils)

# ---------------------------------------------------------------------- output
if [[ -t 1 ]]; then
  G=$'\033[32m'; R=$'\033[31m'; Y=$'\033[33m'; B=$'\033[1m'; O=$'\033[0m'
else
  G=""; R=""; Y=""; B=""; O=""
fi
PASS=0; FAIL=0; WARN=0
ok()   { printf '  %sPASS%s  %s\n' "$G" "$O" "$1"; PASS=$((PASS+1)); }
bad()  { printf '  %sFAIL%s  %s\n' "$R" "$O" "$1"; FAIL=$((FAIL+1)); }
warn() { printf '  %sWARN%s  %s\n' "$Y" "$O" "$1"; WARN=$((WARN+1)); }
info() { printf '        %s\n' "$1"; }
head_(){ printf '\n%s%s%s\n' "$B" "$1" "$O"; }

# ----------------------------------------------------------------------- flags
CHECK_ONLY=0; FORCE=0; SKIP_SDK=0; WITH_CUTTLEFISH=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --check-only)      CHECK_ONLY=1 ;;
    --force)           FORCE=1 ;;
    --skip-sdk)        SKIP_SDK=1 ;;
    --with-cuttlefish) WITH_CUTTLEFISH=1 ;;
    -h|--help)         sed -n '2,20p' "$0"; exit 0 ;;
    *) printf 'unknown option: %s\n' "$1" >&2; exit 3 ;;
  esac
  shift
done

printf '%sFreeDroid AOSP build host setup%s\n' "$B" "$O"
printf 'host: %s   date: %s\n' "$(hostname)" "$(date -u +%Y-%m-%dT%H:%M:%SZ)"

# ================================================================= PREFLIGHT ==
head_ "1. Operating system"
if [[ ! -r /etc/os-release ]]; then
  bad "cannot read /etc/os-release"
else
  # shellcheck disable=SC1091
  . /etc/os-release
  if [[ "${ID:-}" == "ubuntu" && "${VERSION_ID:-}" =~ ^(22\.04|24\.04)$ ]]; then
    ok "$PRETTY_NAME"
  else
    bad "${PRETTY_NAME:-unknown} — AOSP is supported on Ubuntu 22.04 or 24.04"
    info "Other distributions can work but are untested and unsupported upstream."
  fi
fi

ARCH="$(uname -m)"
if [[ "$ARCH" == "x86_64" ]]; then
  ok "architecture x86_64"
else
  bad "architecture $ARCH — AOSP host builds require x86_64"
fi

head_ "2. Capacity"
CORES="$(nproc)"
if   (( CORES >= REQ_CORES_REC )); then ok "CPU cores: $CORES (recommended ≥ $REQ_CORES_REC)"
elif (( CORES >= REQ_CORES_MIN )); then warn "CPU cores: $CORES (minimum $REQ_CORES_MIN met; $REQ_CORES_REC+ recommended)"
else bad "CPU cores: $CORES — minimum is $REQ_CORES_MIN"
     info "A full AOSP build takes roughly 10-20 h on 4 cores versus 1-2 h on 32."
fi

RAM_GB=$(awk '/MemTotal/{printf "%d", $2/1048576}' /proc/meminfo)
if   (( RAM_GB >= REQ_RAM_GB_REC )); then ok "RAM: ${RAM_GB} GiB (recommended ≥ ${REQ_RAM_GB_REC})"
elif (( RAM_GB >= REQ_RAM_GB_MIN )); then warn "RAM: ${RAM_GB} GiB (minimum met; ${REQ_RAM_GB_REC} recommended)"
else bad "RAM: ${RAM_GB} GiB — minimum is ${REQ_RAM_GB_MIN} GiB"
     info "Soong/ninja link and javac steps are memory-hungry; a shortfall is an OOM-kill, not a slowdown."
fi

SWAP_GB=$(awk '/SwapTotal/{printf "%d", $2/1048576}' /proc/meminfo)
if (( SWAP_GB >= REQ_SWAP_GB_MIN )); then ok "swap: ${SWAP_GB} GiB"
else warn "swap: ${SWAP_GB} GiB — ${REQ_SWAP_GB_MIN} GiB recommended"
     info "Without swap a memory spike kills the build outright instead of slowing it."
fi

DISK_GB=$(df -BG --output=avail "$HOME" 2>/dev/null | tail -1 | tr -dc '0-9')
DISK_GB=${DISK_GB:-0}
if   (( DISK_GB >= REQ_DISK_GB_REC )); then ok "disk available on \$HOME: ${DISK_GB} GB"
elif (( DISK_GB >= REQ_DISK_GB_MIN )); then warn "disk available: ${DISK_GB} GB (minimum met; ${REQ_DISK_GB_REC} GB recommended)"
else bad "disk available: ${DISK_GB} GB — minimum is ${REQ_DISK_GB_MIN} GB"
     info "~150 GB source + ~150 GB out/ + ccache + headroom. No shallow-clone trick closes a large gap."
fi

FSTYPE=$(df --output=fstype "$HOME" 2>/dev/null | tail -1 | tr -d ' ')
if [[ "$FSTYPE" =~ ^(ext4|xfs|btrfs)$ ]]; then ok "filesystem: $FSTYPE (case-sensitive)"
else warn "filesystem: ${FSTYPE:-unknown} — AOSP requires a case-sensitive filesystem"; fi

head_ "3. Virtualisation (Cuttlefish / emulator)"
if [[ -e /dev/kvm ]]; then
  ok "/dev/kvm present"
  if [[ -r /dev/kvm && -w /dev/kvm ]]; then ok "/dev/kvm is readable and writable by $(id -un)"
  else warn "/dev/kvm not accessible by $(id -un) — this script adds you to the kvm group"; fi
else
  bad "/dev/kvm ABSENT — Cuttlefish and the Android emulator cannot run"
  if grep -qE '^flags.*\b(vmx|svm)\b' /proc/cpuinfo; then
    info "CPU supports virtualisation; /dev/kvm may appear once the kvm modules load."
  else
    info "CPU exposes no vmx/svm. On a cloud VM this means NESTED VIRTUALISATION IS OFF."
    info "It cannot be enabled from inside the guest — it is a provisioning setting."
  fi
fi

head_ "4. Network"
if curl -fsS --max-time 20 -o /dev/null https://android.googlesource.com/ 2>/dev/null; then
  ok "android.googlesource.com reachable"
else bad "cannot reach android.googlesource.com — repo sync will fail"; fi
if curl -fsS --max-time 20 -o /dev/null https://dl.google.com/android/repository/repository2-3.xml 2>/dev/null; then
  ok "dl.google.com (SDK repository) reachable"
else warn "cannot reach dl.google.com — SDK install will fail"; fi

# --------------------------------------------------------------- verdict ------
head_ "Preflight verdict"
printf '  %d passed, %d failed, %d warnings\n' "$PASS" "$FAIL" "$WARN"
READY=1
if (( FAIL > 0 )); then
  READY=0
  printf '\n  %sHOST IS NOT AOSP-READY%s\n' "$R" "$O"
  printf '  The failures above are provisioning problems, not software ones.\n'
fi

if (( CHECK_ONLY )); then
  printf '\n  --check-only: nothing installed.\n'
  exit $(( READY ? 0 : 1 ))
fi

if (( READY == 0 && FORCE == 0 )); then
  printf '\n  Refusing to install on a host that cannot build AOSP.\n'
  printf '  Re-run with --force to install the toolchain anyway (it will still not be AOSP-ready).\n'
  exit 1
fi
(( READY == 0 )) && printf '\n  %s--force given: installing anyway. This host still cannot build AOSP.%s\n' "$Y" "$O"

# =================================================================== INSTALL ==
SUDO=""
(( EUID != 0 )) && SUDO="sudo"

head_ "5. Build dependencies"
$SUDO apt-get update -qq
MISSING=()
for p in "${PACKAGES[@]}"; do
  dpkg-query -W -f='${Status}' "$p" 2>/dev/null | grep -q "install ok installed" || MISSING+=("$p")
done
if (( ${#MISSING[@]} )); then
  info "installing: ${MISSING[*]}"
  DEBIAN_FRONTEND=noninteractive $SUDO apt-get install -y -qq "${MISSING[@]}"
  ok "installed ${#MISSING[@]} package(s)"
else
  ok "all ${#PACKAGES[@]} build packages already present"
fi

if [[ -e /dev/kvm ]] || (( FORCE )); then
  KMISSING=()
  for p in "${KVM_PACKAGES[@]}"; do
    dpkg-query -W -f='${Status}' "$p" 2>/dev/null | grep -q "install ok installed" || KMISSING+=("$p")
  done
  if (( ${#KMISSING[@]} )); then
    DEBIAN_FRONTEND=noninteractive $SUDO apt-get install -y -qq "${KMISSING[@]}" || warn "some KVM packages failed"
    ok "installed virtualisation packages: ${KMISSING[*]}"
  else
    ok "virtualisation packages already present"
  fi
fi

head_ "6. repo tool"
REPO_BIN="/usr/local/bin/repo"
if [[ -x "$REPO_BIN" ]]; then
  ok "repo already installed at $REPO_BIN"
else
  $SUDO curl -fsSL -o "$REPO_BIN" https://storage.googleapis.com/git-repo-downloads/repo
  $SUDO chmod a+rx "$REPO_BIN"
  ok "installed repo to $REPO_BIN"
fi
"$REPO_BIN" --version >/dev/null 2>&1 && info "$("$REPO_BIN" --version 2>/dev/null | head -1)" || true

head_ "7. Android SDK"
if (( SKIP_SDK )); then
  info "--skip-sdk given"
elif [[ -d "$ANDROID_SDK_DIR/platforms/android-36" && -d "$ANDROID_SDK_DIR/build-tools/35.0.0" ]]; then
  ok "SDK already present at $ANDROID_SDK_DIR (platform 36, build-tools 35.0.0)"
else
  $SUDO mkdir -p "$ANDROID_SDK_DIR/cmdline-tools"
  if [[ ! -x "$ANDROID_SDK_DIR/cmdline-tools/latest/bin/sdkmanager" ]]; then
    TMP="$(mktemp -d)"
    curl -fsSL -o "$TMP/cmdline-tools.zip" \
      "https://dl.google.com/android/repository/$CMDLINE_TOOLS_ZIP"
    unzip -q "$TMP/cmdline-tools.zip" -d "$TMP"
    $SUDO mv "$TMP/cmdline-tools" "$ANDROID_SDK_DIR/cmdline-tools/latest"
    rm -rf "$TMP"
    ok "installed cmdline-tools"
  fi
  export ANDROID_HOME="$ANDROID_SDK_DIR"
  yes 2>/dev/null | $SUDO env ANDROID_HOME="$ANDROID_SDK_DIR" \
    "$ANDROID_SDK_DIR/cmdline-tools/latest/bin/sdkmanager" --licenses >/dev/null 2>&1 || true
  $SUDO env ANDROID_HOME="$ANDROID_SDK_DIR" \
    "$ANDROID_SDK_DIR/cmdline-tools/latest/bin/sdkmanager" --install \
    "$SDK_PLATFORM" "$SDK_BUILD_TOOLS" >/dev/null 2>&1
  if [[ -f "$ANDROID_SDK_DIR/platforms/android-36/android.jar" ]]; then
    ok "installed $SDK_PLATFORM and $SDK_BUILD_TOOLS"
  else
    bad "SDK install did not produce android.jar"
  fi
fi

head_ "8. ccache"
CCACHE_DIR_PATH="${CCACHE_DIR:-/var/cache/ccache}"
$SUDO mkdir -p "$CCACHE_DIR_PATH"
$SUDO chmod 777 "$CCACHE_DIR_PATH" 2>/dev/null || true
if command -v ccache >/dev/null; then
  CCACHE_DIR="$CCACHE_DIR_PATH" ccache -M "$CCACHE_SIZE" >/dev/null 2>&1 || true
  CCACHE_DIR="$CCACHE_DIR_PATH" ccache -o compression=true >/dev/null 2>&1 || true
  ok "ccache configured: $CCACHE_DIR_PATH, max $CCACHE_SIZE, compression on"
else
  warn "ccache not on PATH after install"
fi

head_ "9. Limits and groups"
LIMITS="/etc/security/limits.d/99-aosp.conf"
if [[ -f "$LIMITS" ]]; then
  ok "file descriptor limit already configured ($LIMITS)"
else
  printf '* soft nofile 65536\n* hard nofile 65536\n' | $SUDO tee "$LIMITS" >/dev/null
  ok "raised file descriptor limit to 65536 ($LIMITS)"
  info "Takes effect on next login. AOSP exhausts the default 1024."
fi

TARGET_USER="${SUDO_USER:-$(id -un)}"
for grp in kvm cvdnetwork render; do
  if getent group "$grp" >/dev/null 2>&1; then
    if id -nG "$TARGET_USER" 2>/dev/null | tr ' ' '\n' | grep -qx "$grp"; then
      ok "$TARGET_USER already in group $grp"
    else
      $SUDO usermod -aG "$grp" "$TARGET_USER" && ok "added $TARGET_USER to group $grp"
      info "Group change takes effect on next login."
    fi
  else
    info "group $grp does not exist yet (created by the cuttlefish packages)"
  fi
done

head_ "10. Cuttlefish host packages"
if (( WITH_CUTTLEFISH )); then
  if dpkg-query -W -f='${Status}' cuttlefish-base 2>/dev/null | grep -q "install ok installed"; then
    ok "cuttlefish-base already installed"
  else
    info "building android-cuttlefish debs (this takes a few minutes)"
    CF_DIR="$(mktemp -d)"
    if git clone -q --depth 1 https://github.com/google/android-cuttlefish "$CF_DIR/android-cuttlefish"; then
      (
        cd "$CF_DIR/android-cuttlefish"
        DEBIAN_FRONTEND=noninteractive $SUDO apt-get install -y -qq debhelper config-package-dev >/dev/null 2>&1 || true
        ./tools/buildutils/build_packages.sh >/dev/null 2>&1 &&
          $SUDO dpkg -i ./cuttlefish-base_*.deb ./cuttlefish-user_*.deb >/dev/null 2>&1 || true
        DEBIAN_FRONTEND=noninteractive $SUDO apt-get install -f -y -qq >/dev/null 2>&1 || true
      ) && ok "cuttlefish host packages installed" || warn "cuttlefish package build failed — see docs/development/BUILD_HOST.md"
    else
      warn "could not clone android-cuttlefish"
    fi
    rm -rf "$CF_DIR"
  fi
else
  info "skipped (pass --with-cuttlefish to build and install them)"
  info "Required before Phase 2 can boot an image."
fi

head_ "11. Workspace"
mkdir -p "$AOSP_WORKSPACE"
ok "workspace directory ready: $AOSP_WORKSPACE"
info "NOT version controlled. AOSP is composed here by repo; see ADR-0001."

# ==================================================================== SUMMARY ==
head_ "Summary"
printf '  %d passed, %d failed, %d warnings\n\n' "$PASS" "$FAIL" "$WARN"
cat <<EOF
  Add to your shell profile:

    export ANDROID_HOME=$ANDROID_SDK_DIR
    export PATH="\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools:\$PATH"
    export USE_CCACHE=1
    export CCACHE_EXEC=/usr/bin/ccache
    export CCACHE_DIR=$CCACHE_DIR_PATH

  Then log out and back in (group and ulimit changes need a new session), and:

    ./scripts/verify-build-host.sh

  Phase 1 begins with an UNMODIFIED AOSP baseline — see docs/development/BUILD.md.
EOF

if (( FAIL > 0 )); then
  printf '\n  %sHOST IS NOT AOSP-READY%s — see the failures above.\n' "$R" "$O"
  exit 1
fi
printf '\n  %sSETUP COMPLETE%s\n' "$G" "$O"
