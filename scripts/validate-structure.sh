#!/usr/bin/env bash
#
# FreeDroid repository structure validation.
#
# Runs in any environment: no AOSP tree, no build host, no network required.
# Validates that the repository is structurally sound and that the invariants
# the project depends on still hold.
#
# Usage:  ./scripts/validate-structure.sh
# Exit:   0 = all checks passed, 1 = one or more failures
#
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

PASS=0
FAIL=0
WARN=0

ok()   { printf '  \033[32mPASS\033[0m  %s\n' "$1"; PASS=$((PASS+1)); }
bad()  { printf '  \033[31mFAIL\033[0m  %s\n' "$1"; FAIL=$((FAIL+1)); }
warn() { printf '  \033[33mWARN\033[0m  %s\n' "$1"; WARN=$((WARN+1)); }
head_() { printf '\n\033[1m%s\033[0m\n' "$1"; }

printf '\033[1mFreeDroid structure validation\033[0m\n'
printf 'Repository: %s\n' "$REPO_ROOT"

# ---------------------------------------------------------------------------
head_ "1. Required directories"
# ---------------------------------------------------------------------------
for d in manifests \
         freedroid/launcher freedroid/systemui freedroid/settings \
         freedroid/framework freedroid/services freedroid/apps/store \
         freedroid/updater \
         device/freedroid \
         vendor/freedroid/overlay vendor/freedroid/sepolicy \
         docs/architecture docs/security docs/compatibility \
         docs/development docs/roadmap \
         scripts; do
  [ -d "$d" ] && ok "$d" || bad "$d is missing"
done

# ---------------------------------------------------------------------------
head_ "2. Required documents"
# ---------------------------------------------------------------------------
# Each document must exist and carry real content, not a stub.
MIN_LINES=40
for f in README.md \
         manifests/freedroid.xml \
         docs/architecture/OVERVIEW.md \
         docs/architecture/REPOSITORY_LAYOUT.md \
         docs/security/SECURITY_MODEL.md \
         docs/compatibility/ANDROID_COMPATIBILITY.md \
         docs/development/BUILD.md \
         docs/development/DEVICE_SUPPORT.md \
         docs/development/TESTING.md \
         docs/development/ENVIRONMENT_AUDIT.md \
         docs/roadmap/ROADMAP.md; do
  if [ ! -f "$f" ]; then
    bad "$f is missing"
  else
    n=$(wc -l < "$f")
    if [ "$n" -lt "$MIN_LINES" ]; then
      bad "$f exists but is only $n lines (stub? expected >= $MIN_LINES)"
    else
      ok "$f ($n lines)"
    fi
  fi
done

[ -f docs/architecture/decisions/ADR-0001-overlay-repository-structure.md ] \
  && ok "ADR-0001 present" || bad "ADR-0001 is missing"

# ---------------------------------------------------------------------------
head_ "3. Manifest integrity"
# ---------------------------------------------------------------------------
if python3 -c "import xml.etree.ElementTree as ET; ET.parse('manifests/freedroid.xml')" 2>/dev/null; then
  ok "manifests/freedroid.xml is well-formed XML"
else
  bad "manifests/freedroid.xml is not well-formed XML"
fi

# No fabricated revision SHAs. Real SHAs come from 'repo manifest -r' after a
# real sync; a hand-written one looks authoritative and is worse than none.
if grep -qE 'revision="[0-9a-f]{7,40}"' manifests/freedroid.xml 2>/dev/null; then
  bad "manifests/freedroid.xml contains a hard-coded SHA revision (fabricated pin?)"
else
  ok "no hard-coded SHA revisions in the manifest"
fi

# Phase 0 expects every project to still be commented out.
ACTIVE=$(python3 -c "
import xml.etree.ElementTree as ET
try:
    print(len(ET.parse('manifests/freedroid.xml').getroot().findall('project')))
except Exception:
    print(-1)
" 2>/dev/null)
if [ "$ACTIVE" = "0" ]; then
  ok "manifest has 0 active projects (correct for Phase 0)"
elif [ "$ACTIVE" = "-1" ]; then
  bad "could not parse manifest to count projects"
else
  warn "manifest has $ACTIVE active project(s) - expected 0 until components exist"
fi

if grep -q "TODO(phase-1)" manifests/freedroid.xml; then
  ok "manifest carries its phase-1 pinning TODO"
else
  bad "manifest is missing the TODO marking the AOSP pin as not yet done"
fi

# ---------------------------------------------------------------------------
head_ "4. No AOSP source vendored"
# ---------------------------------------------------------------------------
# ADR-0001: AOSP is composed by repo at sync time, never committed here.
AOSP_MARKERS=0
for m in build/envsetup.sh build/soong frameworks/base system/core art/runtime \
         bionic packages/apps/Launcher3 .repo; do
  if [ -e "$m" ]; then
    bad "AOSP artifact present in repository: $m"
    AOSP_MARKERS=$((AOSP_MARKERS+1))
  fi
done
[ "$AOSP_MARKERS" -eq 0 ] && ok "no AOSP source vendored (ADR-0001 holds)"

# A vendored AOSP tree would be enormous; flag unexpected bulk.
SIZE_KB=$(du -sk --exclude=.git . 2>/dev/null | cut -f1)
if [ "${SIZE_KB:-0}" -gt 51200 ]; then
  warn "working tree is ${SIZE_KB} KB - unexpectedly large for an overlay repo"
else
  ok "working tree size ${SIZE_KB} KB (overlay-sized)"
fi

# ---------------------------------------------------------------------------
head_ "5. No key material or secrets"
# ---------------------------------------------------------------------------
# SECURITY_MODEL.md section 9: no signing keys in the repository, ever.
KEYFILES=$(find . -path ./.git -prune -o \
  \( -name '*.pk8' -o -name '*.pem' -o -name '*.jks' -o -name '*.keystore' \
     -o -name '*.p12' -o -name 'id_rsa*' -o -name '*.key' \) -print 2>/dev/null)
if [ -n "$KEYFILES" ]; then
  bad "key-like files present:"; printf '          %s\n' $KEYFILES
else
  ok "no key files (*.pk8 *.pem *.jks *.keystore *.p12 *.key)"
fi

# NOTE: options must precede the pattern. Using '--' before the pattern makes
# --exclude-dir a FILE operand; grep then exits 2, and under 'pipefail' that
# non-zero status made this check report PASS while a key was present.
# Capture into a variable instead of piping, so no exit status can mask a hit.
KEYBLOCKS=$(grep -rIl --exclude-dir=.git -E '^-{5}BEGIN ([A-Z]+ )*PRIVATE KEY' . 2>/dev/null || true)
if [ -n "$KEYBLOCKS" ]; then
  bad "PRIVATE KEY block found in tracked content:"; printf '          %s\n' $KEYBLOCKS
else
  ok "no PRIVATE KEY blocks"
fi

# Credential-looking assignments with a real value (documentation prose is fine).
CREDS=$(grep -rIn --exclude-dir=.git --exclude='*.md' --exclude='validate-structure.sh' -E \
     '(password|passwd|secret|api_key|apikey|token)[[:space:]]*=[[:space:]]*["'\''][^"'\'']{8,}' \
     . 2>/dev/null || true)
if [ -n "$CREDS" ]; then
  bad "hard-coded credential assignment found in non-documentation file:"
  printf '          %s\n' "$CREDS"
else
  ok "no hard-coded credentials in non-documentation files"
fi

# ---------------------------------------------------------------------------
head_ "6. Branding"
# ---------------------------------------------------------------------------
# Built by concatenation so this file does not itself contain the literal
# string and self-match, which would make the check useless.
BANNED_BRAND="Your""OS"
BRANDHITS=$(grep -rIl --exclude-dir=.git "$BANNED_BRAND" . 2>/dev/null || true)
if [ -n "$BRANDHITS" ]; then
  bad "placeholder branding '$BANNED_BRAND' found in:"; printf '          %s\n' $BRANDHITS
else
  ok "no placeholder branding ('$BANNED_BRAND' absent)"
fi

if grep -rIl --exclude-dir=.git "FreeDroid" docs/ README.md >/dev/null 2>&1; then
  ok "FreeDroid product name used"
else
  bad "product name 'FreeDroid' not found in docs"
fi

# ---------------------------------------------------------------------------
head_ "7. Documentation links"
# ---------------------------------------------------------------------------
LINKOUT=$(python3 - <<'PY'
import os, re
bad = []
n = 0
for dp, dn, fn in os.walk("."):
    if ".git" in dp:
        continue
    for f in fn:
        if not f.endswith(".md"):
            continue
        p = os.path.join(dp, f)
        for m in re.finditer(r'\[[^\]]*\]\(([^)]+)\)', open(p, encoding="utf-8").read()):
            t = m.group(1)
            if t.startswith(("http://", "https://", "#", "mailto:")):
                continue
            t = t.split("#")[0]
            if not t:
                continue
            n += 1
            if not os.path.exists(os.path.normpath(os.path.join(dp, t))):
                bad.append(f"{os.path.relpath(p)} -> {t}")
print(n)
for b in bad:
    print("BROKEN:" + b)
PY
)
NLINKS=$(echo "$LINKOUT" | head -1)
BROKEN=$(echo "$LINKOUT" | grep -c '^BROKEN:' || true)
if [ "$BROKEN" -eq 0 ]; then
  ok "all $NLINKS internal documentation links resolve"
else
  bad "$BROKEN broken internal link(s):"
  echo "$LINKOUT" | grep '^BROKEN:' | sed 's/^BROKEN:/          /'
fi

# ---------------------------------------------------------------------------
head_ "8. Test ID consistency"
# ---------------------------------------------------------------------------
# Every BLD/APP/SEC/DEV id referenced anywhere must be defined in TESTING.md.
if [ -f docs/development/TESTING.md ]; then
  grep -oE '\b(BLD|APP|SEC|DEV)-[0-9]{2}\b' docs/development/TESTING.md | sort -u > /tmp/.fd_defined
  UNDEF=0
  while read -r id; do
    grep -qx "$id" /tmp/.fd_defined || { bad "test ID $id referenced but not defined in TESTING.md"; UNDEF=$((UNDEF+1)); }
  done < <(grep -rhoE '\b(BLD|APP|SEC|DEV)-[0-9]{2}\b' --include='*.md' \
             docs/security docs/compatibility docs/architecture docs/roadmap README.md 2>/dev/null | sort -u)
  [ "$UNDEF" -eq 0 ] && ok "all referenced test IDs are defined in TESTING.md"
  rm -f /tmp/.fd_defined
fi

# ---------------------------------------------------------------------------
head_ "9. Security invariants stated in documentation"
# ---------------------------------------------------------------------------
check_doc() {  # file, pattern, description
  grep -qiE "$2" "$1" 2>/dev/null && ok "$3" || bad "$3 - not stated in $1"
}
check_doc docs/security/SECURITY_MODEL.md \
  'SELinux.*enforcing' "SELinux enforcing is documented"
check_doc docs/security/SECURITY_MODEL.md \
  'never.*INSTALL_PACKAGES|INSTALL_PACKAGES.*withheld|no app holds .INSTALL_PACKAGES' \
  "INSTALL_PACKAGES restriction is documented"
check_doc docs/security/SECURITY_MODEL.md \
  'not implemented|Not tested|design intent' \
  "security model states that nothing is implemented yet"
check_doc docs/compatibility/ANDROID_COMPATIBILITY.md \
  'requires explicit compatibility analysis and testing' \
  "compatibility rule is stated verbatim"
check_doc docs/development/TESTING.md \
  'not evidence' "config-is-not-evidence rule is stated"

# ---------------------------------------------------------------------------
printf '\n\033[1mResult:\033[0m %d passed, %d failed, %d warnings\n' "$PASS" "$FAIL" "$WARN"
if [ "$FAIL" -gt 0 ]; then
  printf '\033[31mVALIDATION FAILED\033[0m\n'
  exit 1
fi
printf '\033[32mVALIDATION PASSED\033[0m\n'
exit 0
