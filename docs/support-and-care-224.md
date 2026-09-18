# Maven Support & Care #224 Mapping

This document maps the independent implementation in this repository to
[Maven Support & Care issue #224](https://github.com/support-and-care/maven-support-and-care/issues/224).

This repository is not an Apache Maven project and is not endorsed by Maven Support & Care.
It is intended as executable input to the discussion: code that demonstrates some of the
hard parts, makes the remaining gaps concrete, and can be donated, adapted, or restarted
under community governance if that is the preferred path.

## Scope discipline

Issue #224 starts with a deliberately narrow Maven-side problem:

- inspect dependencies and build plugins;
- determine whether SBOM evidence is available;
- retrieve OpenSSF Scorecard information;
- support reporting and configurable enforcement;
- remain open to additional checks without duplicating existing vulnerability scanners.

The implementation follows that narrow shape. It is not intended to become another
Dependency-Check, OSS Index, or generic CVE database product.

## Requirement mapping

| #224 concern | Current implementation | Remaining work |
| --- | --- | --- |
| Dependencies | Maven-resolved dependency artifacts are enumerated with type, classifier, repository provenance, and SHA-256. | Broader compatibility matrix and multi-module corpus. |
| Build plugins | Maven build-plugin artifacts are enumerated separately; Resolver provenance and local-file digest fallback cover executed plugins. | More plugin lifecycle/extension/reporting coverage. |
| SBOM published | `sbom-sidecar-available` checks conventional CycloneDX/SPDX sidecars through the same Maven repository context as the consumed artifact. | Public visibility outside that repository context is not yet established. |
| Honest SBOM semantics | `PASS` means repository-context availability only; evidence records `visibility=repository-context`, `contentValidated=false`, and `artifactBound=false`. | Parse/validate the document and bind it to the consumed component/artifact. |
| OpenSSF Scorecard | SCM is read from the Maven-cached POM; artifact/POM provenance disagreement fails closed; Scorecard repo/date/commit are recorded. | Better version-to-source association before any historical artifact claim. |
| Reporting | Schema-versioned deterministic NDJSON is emitted before policy enforcement. | Maven Site/human report for legal and security review. |
| Configurable strictness | Global fail-on-FAIL, fail-on-UNKNOWN, skip, report path, and Scorecard threshold exist. | Per-check enablement and report/warn/fail policy. |
| Additional checks | Internal evidence-provider boundary exists but is intentionally not frozen as a public SPI. | Stabilize provider context after current checks mature. |
| CVE/advisory checks | Not implemented. | If added later, integrate mature data/services rather than reimplementing scanners. |
## Already de-risked

The 0.4 development line already demonstrates the evidence plane that was previously the
largest technical uncertainty:

- Maven Resolver is the repository authority for SBOM sidecars;
- mirrors, authentication, proxies, checksums, local cache, and offline state remain Maven concerns;
- evidence is attached to the artifact Maven resolved rather than to GAV alone;
- artifact and POM repository provenance are recorded independently;
- artifact/POM source disagreement prevents SCM/Scorecard trust;
- dependency and build-plugin observations share the same evidence model;
- machine output distinguishes `PASS`, `FAIL`, and `UNKNOWN` rather than converting
  transport or identity ambiguity into certainty;
- Invoker regression fixtures cover repository binding, positive/negative sidecars, plugin
  identity, and deliberately split JAR/POM provenance.

This is implementation evidence, not a claim that the current repository should be adopted unchanged.

## Proposed funded increment: M2 — evidence integrity and usable policy

- Parse and validate CycloneDX and SPDX documents.
- Match SBOM component identity to the resolved Maven component.
- Bind SBOM evidence to the consumed artifact digest where the publication format permits it.
- Add a separate public-visibility observation instead of inferring publicness from repository access.
- Add per-check enablement and report/warn/fail policy.
- Add a Maven Site or equivalent human-readable report while retaining versioned NDJSON.
- Expand Invoker coverage for multi-module builds, mirrors, offline/cached execution, classifiers,
  and plugin-specific cases.

## Proposed funded increment: M3 — operability and handoff

- Add bounded persistent caching and freshness policy for fixed external APIs.
- Define retry and rate-limit behavior for OpenSSF and future external evidence sources.
- Validate a broader Maven/JDK and enterprise-repository matrix.
- Publish an agreed runnable artifact so reviewers do not need a local source install.
- Dogfood on representative multi-module projects rather than a single showcase dependency.
- Prepare the community handoff path: package/group coordinates, parent POM, issue tracker,
  contributor process, and any Apache contribution or donation work the Maven community requests.

## Unique value relative to existing scanners

Existing vulnerability tools answer whether consumed software is associated with known
vulnerabilities. The #224-shaped value here is different: make the **evidence handles around
what Maven actually resolved — including build plugins — visible and enforceable inside the
same repository context as the build**.

That boundary is why the project deliberately avoids growing a duplicate CVE scanner.

## Governance

The current `io.github.mcc0nnell` coordinates are development coordinates, not a proposal
that Maven depend on a personal namespace. If S&C or the Maven community wants to carry the
work forward, this repository can serve as prior implementation and test material while the
final home, package names, release process, and review path are decided through the normal
community process.
