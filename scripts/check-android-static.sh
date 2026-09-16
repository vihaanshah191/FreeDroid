#!/usr/bin/env bash
#
# FreeDroid Launcher — SDK-independent static checks for the Android modules.
#
# Catches the error classes that are decidable from the sources alone:
# unresolved R.* references, unresolved XML resource references, manifest
# components with no corresponding class, duplicate resource names, missing
# ProGuard files, and unresolved intra-project imports.
#
# THIS IS NOT A SUBSTITUTE FOR COMPILING. It cannot check Kotlin types, Compose
# correctness, or whether an androidx symbol exists. Those require the Android
# SDK. Passing here means only that these specific classes of error are absent.
#
# Usage:  ./scripts/check-android-static.sh
# Exit:   0 = all checks passed, 1 = one or more failures
#
set -uo pipefail
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"
exec python3 - "$REPO_ROOT" <<'PYEOF'
import os, re, sys, glob
import xml.etree.ElementTree as ET

root = sys.argv[1]
os.chdir(root)
APP = "freedroid/launcher/app"
SRC = f"{APP}/src/main"
RES = f"{SRC}/res"
NS  = "{http://schemas.android.com/apk/res/android}"

GREEN, RED, BOLD, OFF = "\033[32m", "\033[31m", "\033[1m", "\033[0m"
fails = []
passes = 0

def check(ok, good, bad):
    global passes
    if ok:
        print(f"  {GREEN}PASS{OFF}  {good}"); passes += 1
    else:
        print(f"  {RED}FAIL{OFF}  {bad}"); fails.append(bad)

def section(t): print(f"\n{BOLD}{t}{OFF}")

print(f"{BOLD}FreeDroid Launcher — Android static checks{OFF}")
if not os.path.isdir(APP):
    print("app module not found"); sys.exit(1)

# ---------------------------------------------------------------- resources
declared = {}
for path in glob.glob(f"{RES}/values/*.xml"):
    for el in ET.parse(path).getroot():
        if isinstance(el.tag, str) and el.get("name"):
            declared.setdefault(el.tag, set()).add(el.get("name"))
for d in glob.glob(f"{RES}/*/"):
    folder = os.path.basename(d.rstrip("/"))
    rtype = folder.split("-")[0]
    if rtype == "values":
        continue
    for f in glob.glob(os.path.join(d, "*")):
        declared.setdefault(rtype, set()).add(os.path.splitext(os.path.basename(f))[0])

kt_sources = glob.glob(f"{SRC}/kotlin/**/*.kt", recursive=True)

section("1. R.* references in Kotlin resolve to declared resources")
bad = []
for path in kt_sources:
    for m in re.finditer(r'\bR\.(\w+)\.(\w+)\b', open(path).read()):
        if m.group(2) not in declared.get(m.group(1), set()):
            bad.append(f"{os.path.relpath(path)}: R.{m.group(1)}.{m.group(2)}")
check(not bad, f"all R.* references resolve", f"unresolved R.* references: {bad}")

section("2. XML @resource references resolve")
bad = []
for path in glob.glob(f"{RES}/**/*.xml", recursive=True) + [f"{SRC}/AndroidManifest.xml"]:
    for m in re.finditer(r'"@(?!android:)(\+?)(\w+)/([\w.]+)"', open(path).read()):
        plus, rtype, name = m.groups()
        if plus:
            continue
        if name not in declared.get(rtype, set()):
            bad.append(f"{os.path.relpath(path)}: @{rtype}/{name}")
check(not bad, "all XML resource references resolve", f"unresolved: {bad}")

section("3. Manifest components resolve to declared classes")
ns_pkg = re.search(r'namespace\s*=\s*"([^"]+)"', open(f"{APP}/build.gradle.kts").read()).group(1)
classes = set()
for path in kt_sources:
    src = open(path).read()
    pkg_m = re.search(r'^package\s+([\w.]+)', src, re.M)
    pkg = pkg_m.group(1) if pkg_m else ""
    for m in re.finditer(r'^(?:\w+\s+)*class\s+(\w+)', src, re.M):
        classes.add(f"{pkg}.{m.group(1)}")
bad = []
for el in ET.parse(f"{SRC}/AndroidManifest.xml").getroot().iter():
    name = el.get(f"{NS}name")
    if name and el.tag in ("activity", "service", "receiver", "provider"):
        fqcn = ns_pkg + name if name.startswith(".") else name
        if fqcn not in classes:
            bad.append(f"<{el.tag}> {name} -> {fqcn}")
check(not bad, f"all manifest components resolve (namespace {ns_pkg})", f"missing classes: {bad}")

section("4. No duplicate resource names")
bad = []
for path in glob.glob(f"{RES}/values/*.xml"):
    seen = set()
    for el in ET.parse(path).getroot():
        if isinstance(el.tag, str) and el.get("name"):
            k = (el.tag, el.get("name"))
            if k in seen:
                bad.append(f"{os.path.relpath(path)}: {k}")
            seen.add(k)
check(not bad, "no duplicate resource names", f"duplicates: {bad}")

section("5. Referenced ProGuard files exist")
# Every "*.pro" string literal in the build file must resolve, relative to the
# module. A nested-paren regex around proguardFiles(...) silently matched
# nothing and passed vacuously, so match the literals directly instead.
bg = open(f"{APP}/build.gradle.kts").read()
refs = re.findall(r'"([^"]+\.pro)"', bg)
missing = [f for f in refs if not os.path.exists(os.path.join(APP, f))]
check(not missing and bool(refs),
      f"all {len(refs)} referenced ProGuard file(s) exist: {refs}",
      f"MISSING ProGuard file(s) {missing} — release build fails" if missing
      else "no ProGuard file referenced (expected at least one for release)")

section("6. Intra-project imports resolve")
own = set()
for path in glob.glob("freedroid/launcher/*/src/*/kotlin/**/*.kt", recursive=True):
    src = open(path).read()
    pkg_m = re.search(r'^package\s+([\w.]+)', src, re.M)
    pkg = pkg_m.group(1) if pkg_m else ""
    for m in re.finditer(r'^(?:\w+\s+)*(?:class|object|interface|enum class|fun|val)\s+(\w+)', src, re.M):
        own.add(f"{pkg}.{m.group(1)}")
    own.add(pkg + ".*")
bad = []
for path in kt_sources + glob.glob("freedroid/launcher/uitest/src/main/kotlin/**/*.kt", recursive=True):
    for m in re.finditer(r'^import\s+(org\.freedroid\.[\w.]+)', open(path).read(), re.M):
        sym = m.group(1)
        if sym not in own and not sym.endswith(".R") and sym.rsplit(".", 1)[0] + ".*" not in own:
            bad.append(f"{os.path.relpath(path)}: {sym}")
check(not bad, "all org.freedroid.* imports resolve", f"unresolved: {bad}")

section("7. Manifest sanity")
m_root = ET.parse(f"{SRC}/AndroidManifest.xml").getroot()
acts = list(m_root.iter("activity"))
check(len(acts) >= 1, f"{len(acts)} activity declared", "no activity declared")
home = [a for a in acts
        if any(c.get(f"{NS}name") == "android.intent.category.HOME"
               for f_ in a.iter("intent-filter") for c in f_.iter("category"))]
check(bool(home), "an activity declares CATEGORY_HOME (eligible as default launcher)",
      "no activity declares CATEGORY_HOME")
for a in acts:
    if list(a.iter("intent-filter")):
        exported = a.get(f"{NS}exported")
        check(exported == "true",
              f"{a.get(f'{NS}name')} with an intent-filter sets exported=true",
              f"{a.get(f'{NS}name')} has an intent-filter but exported={exported} (build error on API 31+)")

section("8. Test module structure")
ui = "freedroid/launcher/uitest"
if os.path.isdir(ui):
    uib = open(f"{ui}/build.gradle.kts").read()
    is_test_plugin = "android.test" in uib
    has_target = "targetProjectPath" in uib
    check(is_test_plugin and has_target,
          "uitest uses com.android.test with targetProjectPath",
          "uitest must use com.android.test + targetProjectPath to depend on an app module")
    check(not os.path.isdir(f"{ui}/src/androidTest"),
          "uitest sources live in src/main (correct for com.android.test)",
          "uitest has src/androidTest, which a com.android.test module does not use")
    # Strip comment lines first: the file's own explanation of why this is
    # forbidden names the forbidden call, and flagging documentation as a
    # violation is how a checker trains people to ignore it.
    uib_code = "\n".join(l for l in uib.splitlines()
                         if not l.lstrip().startswith(("//", "*", "/*")))
    check("project(\":app\")" not in uib_code,
          "uitest does not declare a direct project dependency on :app",
          "uitest declares project(\":app\") — AGP rejects depending on an application module")

print(f"\n{BOLD}Result:{OFF} {passes} passed, {len(fails)} failed")
if fails:
    print(f"{RED}STATIC CHECKS FAILED{OFF}"); sys.exit(1)
print(f"{GREEN}STATIC CHECKS PASSED{OFF}")
PYEOF
