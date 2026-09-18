# Roadmap

The roadmap separates shipped behavior from proposed follow-on work. Items here are not compatibility promises until released.

## Current: 0.4 development line

The 0.4 line hardens the project around the working 0.3 evidence providers:

- conventional Maven project metadata and generated plugin documentation;
- Maven Enforcer requirements for supported Java/Maven versions;
- reproducible-build timestamp metadata;
- Maven Invoker integration tests;
- a user-facing `skip` switch and configurable report path;
- separation of deterministic CI from live Maven Central/OpenSSF smoke tests;
- contributor, security, architecture, capability, and changelog documentation.

## Next: provider maturity

- Stabilize the internal evidence-provider contract before exposing any public SPI.
- Add bounded caching and explicit offline behavior.
- Make provider selection/configuration explicit.
- Improve SCM normalization beyond direct GitHub, Apache GitBox, and parent-POM inheritance.
- Add structured diagnostics suitable for machine policy without changing `PASS/WARN/FAIL/UNKNOWN` semantics.

## Evidence depth

Candidate funded follow-on work:

- validate retrieved CycloneDX/SPDX documents rather than only proving public discoverability;
- bind SBOM evidence more tightly to the Maven artifact/version being consumed;
- verify provenance/attestations where a standard publication path exists;
- add vulnerability/advisory providers without duplicating established scanners blindly;
- support additional project-health/evidence sources behind the same observation model;
- define cache freshness, rate-limit, retry, and offline policy for production CI use.

## Reporting and ecosystem integration

- Maven Site report for project-local evidence summaries.
- Optional SARIF or other CI-friendly exports while retaining NDJSON as the deterministic primitive.
- Maven Central publication of the plugin itself.
- Compatibility validation across a broader Maven/JDK matrix.
- Documentation for enterprise repository managers and mirrors.

## Non-goals

The plugin is not intended to become a general-purpose package manager, an endpoint security product, or a replacement for vulnerability scanners. Its job is to make consumer-side supply-chain evidence visible and enforceable inside Maven.
