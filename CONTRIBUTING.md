# Contributing to Hubitat Drivers and Apps

Thank you for contributing to this project.

## Branch policy

The `development` branch is the primary branch and single source of truth.
Despite its historical name, it contains the current publicly supported versions
of the drivers and apps.

The `main` branch is retained only as a compatibility entry point for older links
and existing Hubitat Package Manager repository registrations. It is not an
independent release channel and must not define separate current package
versions.

The `repository.json` files on `development` and `main` must be identical. For
packages hosted in this repository, both catalogs must point to manifests on
`development`, and those manifests must install drivers, apps, child drivers,
and other executable dependencies from `development`.

Version tags are immutable historical releases. Anyone who needs a fixed,
reproducible version should use a tag instead of a moving branch.

## Development workflow

1. Start from the latest `development` branch.
2. Create a short-lived branch for unfinished work.
3. Use a descriptive name such as `feature/<description>`,
   `fix/<description>`, or `device/<model-or-fingerprint>`.
4. Develop and test the complete change on that branch.
5. Update all related sources, generated drivers, profiles, manifests, versions,
   dates, and release notes together.
6. Merge completed and tested work into `development`.
7. If the package catalog changed, copy the complete `repository.json` to
   `main` and verify that both copies are byte-for-byte identical.

Do not use `development` as a scratch branch. Because users consume it directly,
every commit merged into it should be usable by people following existing forum,
raw-file, and HPM links.

Do not develop or publish drivers and apps independently on `main`. Changes to
`main` are limited to maintaining compatibility entry points such as its
synchronized `repository.json`.

## Canonical targets

- Current publicly supported code: `development`
- Current package manifests: `development`
- Canonical package catalog: `development/repository.json`
- Legacy catalog entry point: `main/repository.json`, identical to the canonical
  catalog
- Unfinished or experimental work: short-lived feature or fix branch
- Reproducible historical release: immutable version tag

For packages hosted in this repository, the complete catalog-to-code chain must
resolve to `development`, even when a user originally registered the
`main/repository.json` URL.

External packages listed in the catalog may use their own canonical repositories
and branches.

## Compatibility and public links

Preserve existing public URLs whenever reasonably possible. Renaming or moving a
publicly linked driver requires either keeping a compatible file at the old path
or documenting and coordinating the migration.

Do not delete `main/repository.json` or turn it into a separate catalog. Existing
HPM registrations may depend on that URL.

## Driver and library changes

When changing a modular driver or shared library:

- Update the corresponding generated `*_lib_included.groovy` file when one is
  distributed.
- Keep source and generated driver versions synchronized.
- Update the package manifest when distributed code or dependencies change.
- Keep existing package and driver UUIDs stable.
- Verify that every manifest location exists on `development`.
- Add release notes for user-visible changes and compatibility fixes.

Generated files are build artifacts and should not be edited independently of
their source drivers and libraries.

## Device support requests

New-device changes should include as much of the following evidence as possible:

- Zigbee model and manufacturer identifiers
- Full Hubitat fingerprint
- Relevant clusters and endpoints
- Tuya data-point identifiers, types, and observed values
- Debug logs showing pairing, reports, and commands
- Exact device model or product listing
- Expected and observed behavior

When evidence is incomplete, mark the work as awaiting device information rather
than guessing behavior into a publicly consumed driver.

## Before merging into `development`

Confirm that:

- The driver loads without errors on the supported Hubitat version.
- Existing supported profiles remain intact.
- Debug logging does not expose secrets or remain permanently noisy.
- Code, generated files, versions, dates, and release notes agree.
- Package manifests reference `development` for packages in this repository.
- Required child drivers and dependencies are included.
- The change is safe for users installing directly from `development`.
- If the catalog changed, both `repository.json` copies are identical.

See [PUBLISHING.md](PUBLISHING.md) for the publication procedure.
