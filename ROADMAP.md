# Roadmap

The roadmap separates shipped development behavior from proposed follow-on work. Items here are not compatibility promises until released.

## Current: 0.4 identity hardening

The 0.4 line hardens the working evidence providers around the artifact Maven actually consumed:

- resolved-artifact identity with type, classifier, repository provenance, and SHA-256;
- independent cached-POM provenance and SHA-256;
- fail-closed SCM association on artifact/POM repository mismatch;
- Maven Resolver sidecar discovery instead of parallel HTTP;
- schema-versioned, serializer-backed NDJSON;
- explicit current-repository semantics for Scorecard, including repo/date/commit;
- deterministic Maven Invoker fixtures with real PASS and FAIL assertions;
- explicit offline behavior;
- generated plugin docs, contributor/security docs, and conventional project metadata.

## Next: evidence binding

Highest-value funded follow-on work:

- parse and validate retrieved CycloneDX/SPDX documents;
- compare SBOM metadata/component identity to the resolved Maven component;
- add a separate public-visibility observation instead of inferring publicness from repository access;
- define artifact-digest binding where the SBOM publication format carries usable hashes;
- consume provenance/attestations where a standard Maven publication path exists;
- improve artifact-version-to-source-commit association before making historical source-health claims.

## Provider maturity

- Stabilize the internal evidence-provider context before exposing a public SPI.
- Add provider selection and per-provider policy.
- Add bounded persistent caching for fixed external APIs.
- Define retry/rate-limit behavior for OpenSSF and future APIs.
- Improve SCM normalization beyond direct GitHub, Apache GitBox, and bounded parent inheritance.
- Add structured diagnostics without weakening PASS/FAIL/UNKNOWN semantics.

## Reporting and ecosystem integration

- Maven Site report for project-local evidence summaries.
- Optional SARIF or other CI-friendly exports while retaining NDJSON as the deterministic primitive.
- Maven Central publication of the plugin itself.
- Compatibility validation across a broader Maven/JDK matrix.
- Documentation for enterprise repository managers and mirrors.

## Later providers

Vulnerability/advisory providers, SLSA/provenance providers, and other evidence sources should only be added once their claims can attach cleanly to the resolved-artifact boundary.

The project should not duplicate mature vulnerability scanners merely to accumulate providers.

## Non-goals

The plugin is not intended to become a package manager, endpoint security product, or generic vulnerability scanner. Its job is to make consumer-side supply-chain evidence visible and enforceable inside Maven without claiming more than the evidence proves.
