#!/usr/bin/env bash
#
# FreeDroid Launcher — constraint checks.
#
# Verifies the launcher obeys the hard rules in docs/architecture/OVERVIEW.md
# and docs/security/SECURITY_MODEL.md. Runs anywhere: no Android SDK, no device,
# no network.
#
# Usage:  ./scripts/check-launcher-constraints.sh
# Exit:   0 = all checks passed, 1 = one or more failures
#
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LAUNCHER="$REPO_ROOT/freedroid/launcher"
cd "$REPO_ROOT"

PASS=0; FAIL=0
ok()  { printf '  \033[32mPASS\033[0m  %s\n' "$1"; PASS=$((PASS+1)); }
bad() { printf '  \033[31mFAIL\033[0m  %s\n' "$1"; FAIL=$((FAIL+1)); }
head_() { printf '\n\033[1m%s\033[0m\n' "$1"; }

printf '\033[1mFreeDroid Launcher constraint checks\033[0m\n'

[ -d "$LAUNCHER" ] || { echo "launcher project not found at $LAUNCHER"; exit 1; }

# Drops matches whose CONTENT is a comment, so documentation *describing* a
# banned pattern is not mistaken for a use of it. Input must be `grep -InH`
# output, i.e. "path:lineno:content" -- always pass -H, because grep omits the
# path when searching a single file and that shifts every field below.
#
# Getting this wrong in either direction is costly: missing real violations
# defeats the check, and flagging the comments that explain the rules trains
# everyone to ignore the output.
drop_comment_matches() {
  awk -F: '{
    content = ""
    for (i = 3; i <= NF; i++) content = content (i > 3 ? ":" : "") $i
    if (content !~ /^[[:space:]]*(\*|\/\/|<!--|#|\/\*)/) print
  }'
}

# ---------------------------------------------------------------------------
head_ "1. No device-category branching"
# ---------------------------------------------------------------------------
DEVICE_PATTERNS='isTablet|isPhone|deviceType|DEVICE_TYPE|smallestScreenWidthDp|SCREENLAYOUT_SIZE|getDefaultDisplay|\.defaultDisplay'
device_hits=$(grep -rInHE "$DEVICE_PATTERNS" \
  --include='*.kt' --include='*.xml' \
  "$LAUNCHER/core/src/main" "$LAUNCHER/app/src" "$LAUNCHER/uitest/src" 2>/dev/null \
  | drop_comment_matches)
if [ -n "$device_hits" ]; then
  bad "device-category check found in source:"; printf '          %s\n' "$device_hits"
else
  ok "no device-category checks outside comments"
fi

# Resource qualifiers that select layout by device class rather than window size.
qualifier_dirs=$(find "$LAUNCHER/app/src" -type d \( -name 'layout-sw*' -o -name 'values-sw*' -o -name 'layout-large*' \) 2>/dev/null)
if [ -n "$qualifier_dirs" ]; then
  bad "device-class resource qualifier directories present:"; printf '          %s\n' $qualifier_dirs
else
  ok "no device-class resource qualifier directories"
fi

# ---------------------------------------------------------------------------
head_ "2. Permissions"
# ---------------------------------------------------------------------------
MANIFEST="$LAUNCHER/app/src/main/AndroidManifest.xml"
if [ ! -f "$MANIFEST" ]; then
  bad "AndroidManifest.xml missing"
else
  BANNED=(INSTALL_PACKAGES REQUEST_INSTALL_PACKAGES SYSTEM_ALERT_WINDOW
          WRITE_SECURE_SETTINGS MANAGE_EXTERNAL_STORAGE READ_EXTERNAL_STORAGE
          WRITE_EXTERNAL_STORAGE INTERNET WRITE_SETTINGS PACKAGE_USAGE_STATS
          REQUEST_DELETE_PACKAGES MOUNT_UNMOUNT_FILESYSTEMS)
  violations=0
  for p in "${BANNED[@]}"; do
    if grep -q "uses-permission android:name=\"android.permission.$p\"" "$MANIFEST"; then
      bad "prohibited permission requested: $p"; violations=$((violations+1))
    fi
  done
  [ "$violations" -eq 0 ] && ok "no prohibited permissions requested"

  # Every permission that IS requested must carry a justifying comment, so the
  # permission list cannot grow silently.
  granted=$(grep -o 'uses-permission android:name="[^"]*"' "$MANIFEST" | sed 's/.*name="//;s/"//')
  count=$(printf '%s\n' "$granted" | grep -c . || true)
  if [ "$count" -le 2 ]; then
    ok "permission surface is minimal ($count requested: $(echo $granted | tr '\n' ' '))"
  else
    bad "permission surface has grown to $count - each needs re-justification"
  fi
fi

# ---------------------------------------------------------------------------
head_ "3. No root, privileged or hidden API use"
# ---------------------------------------------------------------------------
priv_hits=$(grep -rInHE 'Runtime\.getRuntime|ProcessBuilder|SystemProperties|java\.lang\.reflect|sharedUserId|android:sharedUserId' \
  --include='*.kt' --include='*.xml' \
  "$LAUNCHER/core/src" "$LAUNCHER/app/src" "$LAUNCHER/uitest/src" 2>/dev/null | drop_comment_matches)
if [ -n "$priv_hits" ]; then
  bad "privileged/reflective API use found:"; printf '          %s\n' "$priv_hits"
else
  ok "no root, reflection, or hidden-API use"
fi

# ---------------------------------------------------------------------------
head_ "4. No signing material or secrets"
# ---------------------------------------------------------------------------
keyfiles=$(find "$LAUNCHER" -path '*/build' -prune -o \
  \( -name '*.jks' -o -name '*.keystore' -o -name '*.pk8' -o -name '*.pem' -o -name '*.p12' \) -print 2>/dev/null)
if [ -n "$keyfiles" ]; then
  bad "signing material present:"; printf '          %s\n' $keyfiles
else
  ok "no signing material"
fi

signing_hits=$(grep -rInHE 'signingConfig|storePassword|keyPassword|keyAlias' \
  --include='*.kts' --include='*.gradle' "$LAUNCHER" 2>/dev/null | drop_comment_matches)
if [ -n "$signing_hits" ]; then
  bad "signing configuration present in a build script:"; printf '          %s\n' "$signing_hits"
else
  ok "no signing configuration in build scripts"
fi

# ---------------------------------------------------------------------------
head_ "5. Module boundaries"
# ---------------------------------------------------------------------------
core_android=$(grep -rInE '^\s*import (android|androidx)\.' --include='*.kt' "$LAUNCHER/core/src" 2>/dev/null)
if [ -n "$core_android" ]; then
  bad ":core imports an Android class - the policy must stay platform-agnostic:"
  printf '          %s\n' "$core_android"
else
  ok ":core has no Android imports"
fi

if grep -q 'implementation(project(":core"))' "$LAUNCHER/app/build.gradle.kts" 2>/dev/null; then
  ok ":app consumes the :core policy module"
else
  bad ":app does not depend on :core"
fi

# ---------------------------------------------------------------------------
head_ "6. Dependency pinning"
# ---------------------------------------------------------------------------
CATALOG="$LAUNCHER/gradle/libs.versions.toml"
dynamic_hits=$(grep -InHE '=[[:space:]]*"[^"]*\+"|latest\.(release|integration)' "$CATALOG" 2>/dev/null | drop_comment_matches)
if [ -n "$dynamic_hits" ]; then
  bad "dynamic version in the dependency catalogue:"; printf '          %s\n' "$dynamic_hits"
else
  ok "all dependency versions pinned"
fi

# ---------------------------------------------------------------------------
printf '\n\033[1mResult:\033[0m %d passed, %d failed\n' "$PASS" "$FAIL"
[ "$FAIL" -gt 0 ] && { printf '\033[31mCONSTRAINT CHECKS FAILED\033[0m\n'; exit 1; }
printf '\033[32mCONSTRAINT CHECKS PASSED\033[0m\n'
exit 0
