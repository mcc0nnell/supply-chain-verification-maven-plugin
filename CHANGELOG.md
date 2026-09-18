# Changelog

All notable user-visible changes to this project are documented here.

The format is inspired by Keep a Changelog and the project follows semantic versioning while the public surface stabilizes.

## Unreleased

### Added

- Apache-style Maven Invoker integration-test profile.
- Maven-generated help/site metadata.
- Build-environment enforcement for Maven 3.9+ and Java 17+.
- `supplyChainVerification.skip` configuration.
- `supplyChainVerification.reportFile` command-line property.
- Contributor, security, architecture, current-capability, and roadmap documentation.
- Dedicated live-smoke workflow separate from deterministic CI.

### Changed

- Development version advances to `0.4.0-SNAPSHOT`.
- Project metadata now describes the plugin as a maintained Maven component rather than an architecture spike.
- Report-directory creation now also supports a report path without a parent directory.

## 0.3.0 - 2026-09-18

### Added

- Published-POM SCM resolution.
- Parent-POM SCM inheritance.
- Apache GitBox to GitHub canonicalization.
- Live OpenSSF Scorecard retrieval.
- Optional minimum Scorecard score policy.
- Java 17 and Java 21 CI validation.

## 0.2.0

### Added

- Live Maven repository discovery for CycloneDX JSON/XML and SPDX JSON SBOM publication.

## 0.1.0

### Added

- Maven plugin boundary and `verify` goal.
- Deterministic NDJSON evidence model.
- Dependency and build-plugin enumeration.
- Initial demo, CI, and release mechanics.
