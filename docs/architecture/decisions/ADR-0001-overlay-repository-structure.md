# ADR-0001 — FreeDroid is an overlay repository, not an AOSP fork

- **Status:** Accepted
- **Date:** 2026-09-16
- **Deciders:** Project lead (approved), lead OS engineer (proposed)
- **Supersedes:** The illustrative `freedroid/aosp/` layout in the project brief

## Context

The project brief sketched a repository layout containing an `aosp/` directory,
with the caveat that the structure should not be assumed optimal until the AOSP
build architecture had been inspected. This ADR records that inspection's
conclusion.

AOSP is not a single git repository. It is approximately 1,000 independent git
repositories, orchestrated by the `repo` tool against an XML manifest that pins
each project to a revision. `repo` composes them into a single source tree at
sync time. Soong (`Android.bp`) and Kati/Make (`Android.mk`) then build across
that composed tree; no meaningful component builds in isolation from it.

FreeDroid must remain maintainable across future Android releases
(project requirement 16) and must retain broad Android application compatibility
(requirement 1). Both properties depend on our ability to move to a new upstream
release without heroics.

## Decision

**This repository contains only the FreeDroid delta.** AOSP source is fetched
from `android.googlesource.com` at a pinned release tag and combined with this
repository at sync time via `.repo/local_manifests/`.

The composed workspace is a scratch directory on a build host. It is never
tracked in git.

```text
aosp-workspace/                      # build host scratch — NOT in version control
├── .repo/
│   ├── manifests/                   # upstream AOSP manifest @ android-16.0.0_r4
│   └── local_manifests/
│       └── freedroid.xml            # ← from this repo; adds FreeDroid projects
├── frameworks/  system/  packages/  # ← upstream AOSP, untouched
├── device/freedroid/                # ← from this repo
└── vendor/freedroid/                # ← from this repo
```

## Rationale

1. **Upstream tracking.** `repo` tracks each project's upstream independently.
   Flattening AOSP into one git repository discards that, and with it any
   practical path to `repo sync`-ing a newer Android release.

2. **Requirement 16 (maintainability across Android versions).** With an overlay,
   moving from Android 16 to Android 17 means re-pinning a tag, re-syncing, and
   resolving conflicts confined to our own delta. With a vendored fork, it means
   a manual merge across a million upstream files. The first is a routine
   operation; the second is a rewrite that would happen at most once.

3. **Diff legibility.** A reviewer can read the entire FreeDroid delta. That is
   a security property, not just an ergonomic one — see the supply-chain section
   of the security model. A delta hidden inside a vendored AOSP copy cannot be
   audited by inspection.

4. **Repository size.** A vendored AOSP tree is hundreds of gigabytes. Every
   contributor would clone it. The overlay is megabytes.

5. **Licensing hygiene.** Upstream code keeps its upstream provenance, history,
   and license headers rather than being absorbed into our history.

## Consequences

### Positive

- Upstream rebases are tractable and routine.
- The complete FreeDroid delta is small enough to review in full.
- Contributors clone megabytes, not hundreds of gigabytes.
- Security-only upstream merges (from `android16-security-release`) can be taken
  independently of feature releases — this is what makes requirement 15
  (independently deployable security updates) achievable.

### Negative

- A working tree requires a `repo sync` (~100+ GB, hours on first run). There is
  no clone-and-build path. Mitigated by documented setup in `BUILD.md` and by
  build-host reference images.
- Changes that genuinely require modifying upstream source must be expressed as
  patch files with a documented apply order, which is more friction than editing
  a vendored tree. **This friction is intentional.** Each upstream patch is a
  permanent maintenance liability, and the cost of adding one should be felt at
  the moment it is added, not at the next release rebase.
- Reproducibility depends on pinned manifest revisions. Mitigated by recording
  exact SHAs (`repo manifest -r -o`) for every release build and archiving them
  alongside release artifacts.

## Customization hierarchy

A direct consequence of this decision: prefer mechanisms that require **no**
upstream modification. Descending order of preference, with the cost of each:

| # | Mechanism | Upstream cost | Use for |
| --- | --- | --- | --- |
| 1 | Runtime Resource Overlay (RRO) | none | Branding, colors, strings, config flags |
| 2 | Product configuration / `PRODUCT_PACKAGES` | none | Swapping components, e.g. FreeDroidLauncher for Launcher3 |
| 3 | New standalone modules | none | Store, Updater, FreeDroid services |
| 4 | Additive SELinux policy | none | New domains for new services |
| 5 | Patch file against upstream | **one patch to rebase per release** | Only when 1–4 genuinely cannot express the change |
| 6 | Forking an AOSP component | **permanent divergence** | Requires written compatibility + security justification and lead sign-off |

Mechanisms 5 and 6 require the justification recorded in
`docs/compatibility/ANDROID_COMPATIBILITY.md`. "It was easier" is not a
justification.

## Alternatives considered

**Vendored AOSP (the brief's sketch).** Rejected for the reasons above. The
decisive factor is requirement 16: it makes future Android adoption
impractical.

**Full fork with our own manifest of forked projects** (the LineageOS model,
where many AOSP projects are forked into the org). Rejected for now as premature.
It is the right answer when the delta is large and touches many projects; our
delta should stay small. This is revisitable if the patch set grows to the point
where patch-file management costs more than forking would — that threshold
should be an explicit, measured decision, not a drift.

**GrapheneOS-style hardened fork.** Out of scope as a structural model: that
project's delta is deliberately deep in the platform. FreeDroid's goals center on
distribution freedom with security preserved, not on platform hardening research.
Their published work remains a useful reference for specific mitigations.
