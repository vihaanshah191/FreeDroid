# FreeDroid — Repository Layout

**Decision record:** [ADR-0001](decisions/ADR-0001-overlay-repository-structure.md)
**Status:** Approved. Directories are created as their phase begins, not up front.

---

## 1. The two trees

There are two distinct things, and conflating them is the mistake this layout
exists to prevent.

### The FreeDroid repository (this repo) — version controlled, megabytes

```text
FreeDroid/
├── README.md
├── manifests/
│   └── freedroid.xml                  # local manifest: FreeDroid projects + AOSP pin
├── freedroid/
│   ├── launcher/                      # FreeDroidLauncher
│   ├── systemui/                      # SystemUI extensions (overlay-first)
│   ├── settings/                      # Settings injection fragments
│   ├── framework/                     # framework extensions + FreeDroid SDK stubs
│   ├── services/                      # system services (SourceTrust, Update)
│   ├── apps/                          # Store client, first-party apps
│   └── updater/                       # OTA client, update_engine integration
├── device/
│   └── freedroid/
│       ├── cuttlefish_x86_64/         # Phase 1–3 target
│       └── common/                    # shared device config
├── vendor/
│   └── freedroid/
│       ├── config/                    # product makefiles, build variants
│       ├── overlay/                   # RROs — branding, colors, strings, configs
│       ├── sepolicy/                  # additive SELinux policy
│       ├── prebuilts/                 # boot animation, wallpapers, fonts
│       └── security/                  # TEST keys only, clearly marked. Never release keys.
├── patches/                           # upstream patches, if any. Each needs justification.
│   └── APPLY_ORDER.md
├── docs/
│   ├── architecture/  security/  compatibility/  development/  roadmap/
└── scripts/
    ├── sync.sh  build.sh  verify-build-variant.sh  run-security-tests.sh
```

### The build workspace — scratch on the build host, **never** in git

```text
aosp-workspace/
├── .repo/
│   ├── manifests/                     # AOSP manifest @ android-16.0.0_r4
│   └── local_manifests/
│       └── freedroid.xml              # symlink/clone from manifests/ above
├── build/  frameworks/  system/  packages/  art/  bionic/    # upstream AOSP
├── device/freedroid/                  # ← this repo, placed by repo sync
├── vendor/freedroid/                  # ← this repo, placed by repo sync
└── out/                               # build output, ~150 GB
```

`repo sync` places this repository's projects at their manifest-declared paths
inside the workspace. Upstream AOSP and FreeDroid code sit side by side in the
build tree while remaining separate in version control. That is the whole trick.

## 2. Where a change belongs

Consult this table before writing code. Most changes belong further up than the
first instinct suggests.

| I want to… | Goes in | Mechanism | Upstream cost |
| --- | --- | --- | --- |
| Change a system color, string, icon, or boot animation | `vendor/freedroid/overlay/` or `prebuilts/` | RRO / prebuilt | none |
| Change an AOSP config default (`config.xml`, `bools`, `arrays`) | `vendor/freedroid/overlay/` | RRO | none |
| Replace the launcher | `vendor/freedroid/config/` + `freedroid/launcher/` | `PRODUCT_PACKAGES` | none |
| Add a quick settings tile | `freedroid/systemui/` | `config_quickSettingsTiles` RRO + tile module | none |
| Add a Settings screen | `freedroid/settings/` | Settings injection (`SettingsInjector` / `<meta-data>` activity) | none |
| Add a system service | `freedroid/services/` + `vendor/freedroid/sepolicy/` | new module + additive policy | none |
| Add a public FreeDroid API for apps | `freedroid/framework/` | separate SDK library, **not** `framework.jar` | none |
| Change AOSP framework behavior | `patches/` | patch file + **justification + sign-off** | one rebase per release |
| Fork an AOSP component | manifest `remove-project` + fork | **written compatibility + security case, lead sign-off** | permanent |

Rows 1–7 require no upstream modification. Rows 8–9 do, and each carries the
review gate in
[`ANDROID_COMPATIBILITY.md`](../compatibility/ANDROID_COMPATIBILITY.md) §5.

## 3. Rules

### Never in this repository

- **AOSP source.** If a file's upstream is `android.googlesource.com`, it does not
  get committed here. Express the change as an RRO, a new module, or a patch file.
- **Release signing keys, of any kind, in any encoding.** Not the private keys,
  not encrypted blobs of them, not "temporarily." See
  [`SECURITY_MODEL.md`](../security/SECURITY_MODEL.md) §T10.
- **Build output.** `out/`, `*.img`, `*.apk` artifacts, `target_files*.zip`.
- **Proprietary vendor blobs** without documented redistribution rights.

### Test keys

`vendor/freedroid/security/` may contain **test** keys only. They must be:

- Named unambiguously (`freedroid-testkey.*`), never `releasekey` or `platform`.
- Accompanied by a `README.md` stating in plain terms that they are public,
  worthless, and that any image signed with them is untrusted.
- Rejected by the release gate — `scripts/verify-build-variant.sh` fails a `user`
  build signed with a test key. The gate is the actual protection; the naming
  convention only helps humans notice sooner.

AOSP's own `build/target/product/security/` test keys are publicly known. An image
signed with them offers no authenticity guarantee whatsoever and must never leave
a development machine.

### Patches

Every file in `patches/` needs:

1. The upstream project and the exact base revision it applies to.
2. Why mechanisms 1–4 in the ADR's customization hierarchy could not express it.
3. Its compatibility impact — which app-visible behavior changes, if any.
4. Its security impact.
5. An owner responsible for rebasing it each release.

`patches/APPLY_ORDER.md` records the order and the audit trail. A patch without
an owner gets dropped at the next rebase; that is the intended failure mode,
because an unowned patch is an unmaintained one.

## 4. Manifest strategy

`manifests/freedroid.xml` is a `repo` local manifest. It adds FreeDroid projects
to the upstream tree; it does not restate upstream projects.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<manifest>
  <remote name="freedroid" fetch="https://github.com/vihaanshah191/" />

  <project path="device/freedroid" name="FreeDroid"
           remote="freedroid" revision="main" />
  <!-- Additional FreeDroid projects added as their phases begin. -->

  <!-- <remove-project> entries appear here ONLY if an AOSP project is
       genuinely forked. Each requires the sign-off in ADR-0001. As of
       Phase 0 there are none, and keeping it that way is a project goal. -->
</manifest>
```

The upstream pin lives in the `repo init` invocation and is recorded in
`docs/development/BUILD.md`:

```bash
repo init -u https://android.googlesource.com/platform/manifest -b android-16.0.0_r4
```

**Reproducibility:** every release build records `repo manifest -r -o manifest-<build-id>.xml`,
which pins every project to an explicit SHA. That file is archived with the release
artifacts. Without it, a release cannot be rebuilt, and an unrebuildable release
cannot be audited after the fact.

## 5. Two-stream branching

```text
android-16.0.0_r4 ──────────────────────► FreeDroid feature releases
  (platform tags)                          full test pass, planned

android16-security-release ─────────────► FreeDroid security-only releases
  (monthly SPL)                            minimal diff, fast path,
                                           independently shippable
```

The second stream is the concrete implementation of requirement 15 — security
updates that ship without waiting on a feature release. It works only if the
FreeDroid delta stays small enough that a security merge does not drag feature
work with it. This is the practical reason the customization hierarchy is
enforced rather than merely recommended.

## 6. Directory creation policy

Directories in §1 are created **as their phase begins**, not now. An empty
directory tree with placeholder files is inventory, not architecture: it implies
work that does not exist and goes stale before it is used.

Current state (Phase 0): `docs/`, `README.md`. Everything else is planned.
