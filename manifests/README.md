# `manifests/` — repo local manifests

| File | Purpose | Status |
| --- | --- | --- |
| `freedroid.xml` | `repo` local manifest adding FreeDroid projects to an AOSP tree | **Placeholder — all projects disabled** |

## What this is

A local manifest **adds** projects to the upstream AOSP manifest; it does not
replace it. On the build host it is placed at
`<workspace>/.repo/local_manifests/freedroid.xml`, and `repo sync` composes
upstream AOSP and FreeDroid into one build tree.

## Current state

- **No AOSP tree has been synced.**
- **No AOSP revision has been pinned.**
- Every `<project>` in `freedroid.xml` is commented out, because none of the
  components exist yet.
- Planned baseline is `android-16.0.0_r4`, selected in
  [`ENVIRONMENT_AUDIT.md`](../docs/development/ENVIRONMENT_AUDIT.md) §6 and
  confirmed to exist upstream by a read-only query. Nothing has been fetched.

## Rules

- **Never hand-write project SHAs.** Generate them with `repo manifest -r -o`
  after a real sync. A fabricated SHA looks authoritative and is worse than none.
- The upstream tag is pinned by `repo init -b <tag>` on the build host, not here.
- A `<remove-project>` entry means an AOSP fork. There are zero, and that is a
  goal, not an accident.
