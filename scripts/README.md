# `scripts/` — development and release tooling

| Script | Purpose | Status |
| --- | --- | --- |
| `validate-structure.sh` | Validates repository structure, docs, and manifest | ✅ implemented |
| `sync.sh` | Sync an AOSP workspace with the FreeDroid overlay | ⬜ Phase 1 |
| `build.sh` | Build wrapper with variant selection | ⬜ Phase 1 |
| `verify-build-variant.sh` | **Release gate** — fails a `user` build carrying debug settings | ⬜ Phase 9 |
| `run-security-tests.sh` | Execute the SEC suite against a booted image | ⬜ Phase 9 |

## `validate-structure.sh`

Runs in this container — no AOSP, no build host, no network required. Checks:

- Required directories exist
- Required documents exist and are non-trivial
- `manifests/freedroid.xml` is well-formed XML
- No AOSP source has been vendored
- No private key material is committed
- No fabricated SHAs in the manifest
- Internal documentation links resolve

```bash
./scripts/validate-structure.sh          # exit 0 = pass, 1 = fail
```

**The validator is itself tested against planted violations.** A check that has
never been observed to fail is a check nobody has shown to work — the same
principle SEC-16 applies to the release gate. Doing this caught a real bug: the
private-key check used `grep -rIlE -- 'pattern' --exclude-dir=.git`, where `--`
before the pattern turns `--exclude-dir` into a file operand. `grep` then exits 2,
and under `set -o pipefail` that non-zero status made the `if` take the else
branch — so the check reported **PASS while a private key was present in the
tree**. Options now precede the pattern, and every content check captures output
into a variable rather than relying on a pipeline's exit status.

## `verify-build-variant.sh` (Phase 9)

The release gate. A `user` build fails if it carries `ro.debuggable=1`, ADB
enabled, a permissive SELinux domain, a test-key signature, debug apps, or
dm-verity disabled.

A checklist is not a control. This gate is the control — and per test SEC-16 it
must itself be tested by feeding it a deliberately bad image.
