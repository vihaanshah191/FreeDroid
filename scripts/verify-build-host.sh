#!/usr/bin/env bash
#
# FreeDroid — AOSP build host verification.
#
# Checks that a host is genuinely ready to sync and build AOSP. Read-only:
# installs nothing, changes nothing, downloads nothing but small metadata.
#
# Deliberately separate from setup-build-host.sh. "The setup script ran" and
# "this host can build AOSP" are different claims, and only the second matters.
# This one re-derives everything from the live system rather than trusting that
# an earlier run did what it said.
#
#   ./scripts/verify-build-host.sh
#
# Exit: 0 ready · 1 not ready
#
set -uo pipefail

readonly REQ_CORES_MIN=16
readonly REQ_RAM_GB_MIN=64
readonly REQ_DISK_GB_MIN=400
readonly ANDROID_SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_DIR:-/opt/android-sdk}}"

if [[ -t 1 ]]; then
  G=$'\033[32m'; R=$'\033[31m'; Y=$'\033[33m'; B=$'\033[1m'; O=$'\033[0m'
else G=""; R=""; Y=""; B=""; O=""; fi
PASS=0; FAIL=0; WARN=0
ok()   { printf '  %sPASS%s  %s\n' "$G" "$O" "$1"; PASS=$((PASS+1)); }
bad()  { printf '  %sFAIL%s  %s\n' "$R" "$O" "$1"; FAIL=$((FAIL+1)); }
warn() { printf '  %sWARN%s  %s\n' "$Y" "$O" "$1"; WARN=$((WARN+1)); }
info() { printf '        %s\n' "$1"; }
head_(){ printf '\n%s%s%s\n' "$B" "$1" "$O"; }

printf '%sFreeDroid build host verification%s\n' "$B" "$O"
printf 'host: %s   user: %s   date: %s\n' "$(hostname)" "$(id -un)" "$(date -u +%Y-%m-%dT%H:%M:%SZ)"

head_ "1. Capacity"
CORES=$(nproc)
(( CORES >= REQ_CORES_MIN )) && ok "CPU cores: $CORES" || bad "CPU cores: $CORES (need ≥ $REQ_CORES_MIN)"
RAM_GB=$(awk '/MemTotal/{printf "%d", $2/1048576}' /proc/meminfo)
(( RAM_GB >= REQ_RAM_GB_MIN )) && ok "RAM: ${RAM_GB} GiB" || bad "RAM: ${RAM_GB} GiB (need ≥ ${REQ_RAM_GB_MIN})"
SWAP_GB=$(awk '/SwapTotal/{printf "%d", $2/1048576}' /proc/meminfo)
(( SWAP_GB >= 16 )) && ok "swap: ${SWAP_GB} GiB" || warn "swap: ${SWAP_GB} GiB (16 GiB recommended)"
DISK_GB=$(df -BG --output=avail "$HOME" 2>/dev/null | tail -1 | tr -dc '0-9'); DISK_GB=${DISK_GB:-0}
(( DISK_GB >= REQ_DISK_GB_MIN )) && ok "disk free on \$HOME: ${DISK_GB} GB" || bad "disk free: ${DISK_GB} GB (need ≥ ${REQ_DISK_GB_MIN})"

head_ "2. Toolchain"
check_cmd() { # name, note
  if command -v "$1" >/dev/null 2>&1; then ok "$1: $(command -v "$1")"; else bad "$1 NOT FOUND${2:+ — $2}"; fi
}
soft_cmd() { # name, why-optional
  if command -v "$1" >/dev/null 2>&1; then ok "$1: $(command -v "$1")"; else warn "$1 not on PATH — $2"; fi
}
check_cmd git
check_cmd repo "install with setup-build-host.sh"
check_cmd python3
check_cmd ccache
check_cmd java
check_cmd make
# AOSP builds with its own prebuilt ninja from prebuilts/build-tools, so a host
# copy is convenient but not required. Reporting it as a failure would be wrong.
soft_cmd ninja "AOSP uses its own prebuilt; host copy is optional"

if command -v java >/dev/null 2>&1; then
  JV=$(java -version 2>&1 | grep -oE '"[0-9]+' | tr -d '"' | head -1)
  if [[ -n "${JV:-}" ]] && (( JV >= 17 )); then ok "Java major version: $JV"
  else warn "Java major version: ${JV:-unknown} (17+ expected; AOSP uses its own prebuilt JDK regardless)"; fi
fi

MISSING_PKGS=()
for p in flex bison build-essential zip unzip rsync zlib1g-dev libc6-dev-i386 \
         libncurses-dev lib32z1-dev libgl1-mesa-dev libxml2-utils xsltproc \
         fontconfig lz4 gperf imagemagick xmlstarlet libssl-dev; do
  dpkg-query -W -f='${Status}' "$p" 2>/dev/null | grep -q "install ok installed" || MISSING_PKGS+=("$p")
done
if (( ${#MISSING_PKGS[@]} == 0 )); then ok "all AOSP build packages installed"
else bad "missing packages: ${MISSING_PKGS[*]}"; fi

head_ "3. Android SDK"
if [[ -f "$ANDROID_SDK_DIR/platforms/android-36/android.jar" ]]; then
  ok "platform 36 present ($ANDROID_SDK_DIR)"
else bad "platforms;android-36 missing at $ANDROID_SDK_DIR"; fi
if [[ -x "$ANDROID_SDK_DIR/build-tools/35.0.0/aapt2" ]]; then ok "build-tools 35.0.0 present (aapt2, d8)"
else bad "build-tools;35.0.0 missing"; fi
[[ -n "${ANDROID_HOME:-}" ]] && ok "ANDROID_HOME is set: $ANDROID_HOME" \
  || warn "ANDROID_HOME not set in this shell — :app and :uitest will be skipped by the launcher build"

head_ "4. Virtualisation"
if [[ -e /dev/kvm ]]; then
  ok "/dev/kvm present"
  if [[ -r /dev/kvm && -w /dev/kvm ]]; then ok "/dev/kvm accessible by $(id -un)"
  else bad "/dev/kvm not accessible by $(id -un) — add to the kvm group and re-login"; fi
else
  bad "/dev/kvm ABSENT — Cuttlefish cannot run (Phases 2+ blocked)"
  grep -qE '^flags.*\b(vmx|svm)\b' /proc/cpuinfo \
    && info "CPU supports virtualisation; load the kvm modules." \
    || info "CPU exposes no vmx/svm — nested virtualisation is off at the hypervisor."
fi
if dpkg-query -W -f='${Status}' cuttlefish-base 2>/dev/null | grep -q "install ok installed"; then
  ok "cuttlefish-base installed"
else warn "cuttlefish host packages not installed — run setup with --with-cuttlefish before Phase 2"; fi

head_ "5. Limits"
NOFILE=$(ulimit -n)
(( NOFILE >= 65536 )) && ok "open file limit: $NOFILE" \
  || warn "open file limit: $NOFILE (65536 expected; log out and back in after setup)"

head_ "6. ccache"
if command -v ccache >/dev/null 2>&1; then
  SZ=$(ccache -s 2>/dev/null | grep -iE "max.*size|max_size" | head -1 | tr -s ' ')
  ok "ccache present${SZ:+ — $SZ}"
  [[ "${USE_CCACHE:-}" == "1" ]] && ok "USE_CCACHE=1" || warn "USE_CCACHE not set to 1 in this shell"
else bad "ccache not installed"; fi

head_ "7. Upstream reachability"
if git ls-remote --heads https://android.googlesource.com/platform/manifest main >/dev/null 2>&1; then
  ok "AOSP manifest repository reachable and readable"
else bad "cannot read the AOSP manifest — repo sync will fail"; fi

head_ "8. FreeDroid repository"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
[[ -f "$REPO_ROOT/manifests/freedroid.xml" ]] && ok "FreeDroid overlay present at $REPO_ROOT" \
  || warn "FreeDroid overlay not found — clone it before repo init"
if [[ -f "$REPO_ROOT/manifests/freedroid.xml" ]]; then
  if python3 -c "import xml.etree.ElementTree as E; E.parse('$REPO_ROOT/manifests/freedroid.xml')" 2>/dev/null; then
    ok "local manifest is well-formed XML"
  else bad "local manifest is not well-formed"; fi
fi

head_ "Verdict"
printf '  %d passed, %d failed, %d warnings\n' "$PASS" "$FAIL" "$WARN"
if (( FAIL > 0 )); then
  printf '\n  %sNOT READY%s — %d blocking issue(s) above.\n' "$R" "$O" "$FAIL"
  exit 1
fi
printf '\n  %sBUILD HOST READY%s\n' "$G" "$O"
printf '  Next: Phase 1 — sync and build an UNMODIFIED AOSP baseline.\n'
printf '        See docs/development/BUILD.md section 5.\n'
