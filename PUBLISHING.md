# Publishing Policy

## Purpose

This document defines how changes are published without breaking existing
Hubitat Community links, raw installation URLs, or Hubitat Package Manager
registrations.

## Single source of truth

`development` is the primary branch, canonical public branch, and single source
of truth. A merge into `development` is a public publication even when no GitHub
Release or version tag is created.

Only completed and tested work should be merged into `development`.

## Role of `main`

`main` is a compatibility branch retained for older links and existing HPM
repository registrations. It is not a second release channel.

Its `repository.json` must be byte-for-byte identical to
`development/repository.json`. Both catalog entry points must resolve packages
hosted in this repository to manifests on `development`, and those manifests
must install executable files from `development`.

Driver and app versions must not be developed or published independently on
`main`.

## Resolution model

```text
development/repository.json --+
                             +--> development manifest --> development code
main/repository.json --------+
```

External packages in the catalog may continue to use their own canonical
repositories and branches.

## Canonical URL policy

| Purpose | Target |
| --- | --- |
| Current publicly supported code | `development` |
| Current internal package manifest | `development` |
| Canonical HPM catalog | `development/repository.json` |
| Legacy HPM catalog entry point | `main/repository.json` |
| Experimental build | feature or fix branch |
| Reproducible historical version | immutable version tag |

## Publishing a driver or app

Before merging into `development`:

1. Complete functional testing on a feature or fix branch.
2. Update the source driver and affected shared libraries.
3. Regenerate distributed `*_lib_included.groovy` files.
4. Update the driver or app version.
5. Update the release date and release notes.
6. Update every associated package manifest.
7. Include required child drivers and dependencies.
8. Confirm that all internal manifest and executable URLs use `development`.
9. Confirm that every referenced file exists.
10. Keep existing package and driver UUIDs unchanged.
11. Ensure code, generated artifacts, metadata, and documentation describe the
    same release.
12. Merge the complete change into `development`.
13. Test a fresh installation or upgrade through the public development URLs.

If testing reveals a serious problem, publish a focused corrective change to
`development`. Do not rewrite published branch history.

## Updating the package catalog

Whenever `development/repository.json` changes:

1. Validate its JSON syntax.
2. Verify every package location.
3. Confirm that packages hosted in `kkossev/Hubitat` point to manifests on
   `development`.
4. Confirm that each internal manifest installs code from `development`.
5. Copy the complete catalog to `main/repository.json`; do not maintain two
   hand-edited variants.
6. Verify that the two files are byte-for-byte identical.
7. Test package discovery using both repository URLs.
8. Confirm that both entry points resolve to the same manifests and code.

Updating the compatibility catalog does not publish driver source from `main`.

## Migrating legacy `main` references

Migrate existing internal catalog entries in small, verified batches:

1. Confirm the package manifest exists on `development`.
2. Correct its version, date, release notes, dependencies, and executable URLs.
3. Test the manifest directly from `development`.
4. Change the catalog location from `/main/` to `/development/`.
5. Test HPM discovery, installation, and upgrade.
6. Synchronize the complete catalog to `main`.

Do not change all legacy entries at once unless every target manifest has already
been validated. A gradual migration limits the impact of a bad manifest.

## Versioned releases

For an immutable historical release:

1. Select a verified commit already present in `development`.
2. Confirm that code and package metadata use the intended version.
3. Create an annotated version tag using the established naming convention.
4. Publish release notes describing user-visible changes, compatibility changes,
   supported devices, and known limitations.
5. Never move, replace, or reuse the published tag.

## Required consistency checks

Every publication must keep these synchronized:

- Driver or app version
- Generated driver version
- Package manifest version
- Release date and release notes
- Required child drivers and libraries
- Manifest and executable locations
- Top-level catalog entry

A publication is incomplete when any of these describes an older or different
release.

## Emergency fixes

Emergency fixes follow the normal direction:

```text
fix branch -> development -> synchronized main/repository.json if catalog changed
```

Do not patch driver or app code on `main`. Publish the fix to `development`, then
synchronize the compatibility catalog only if its contents changed.
