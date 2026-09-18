# Current Capabilities

This document is the authoritative “what works now” boundary for the 0.4 development line.

| Capability | Status | Notes |
| --- | --- | --- |
| Enumerate resolved Maven dependencies | Supported | Includes transitive dependencies visible to the project |
| Enumerate resolved build-plugin artifacts | Supported | Uses Maven's resolved plugin artifacts |
| Capture artifact type/classifier/extension | Supported | Preserved in report identity |
| Capture artifact SHA-256 | Supported | Hashes the local file Maven/Resolver materialized, including executed build plugins |
| Capture POM SHA-256 | Supported | Hashes the cached Maven POM when available |
| Artifact repository provenance | Supported | Maven Resolver local-repository provenance |
| POM repository provenance | Supported | Independently recorded |
| Detect artifact/POM repository mismatch | Supported | Scorecard/SCM fails closed to UNKNOWN |
| CycloneDX JSON sidecar discovery | Supported | Maven Resolver, consumed artifact's repository |
| CycloneDX XML sidecar discovery | Supported | Maven Resolver, consumed artifact's repository |
| SPDX JSON sidecar discovery | Supported | Maven Resolver, consumed artifact's repository |
| Public visibility of SBOM sidecar | Not yet | Repository-context availability does not prove unauthenticated public reachability |
| Maven mirror/auth/proxy/offline behavior for sidecars | Supported | Delegated to Maven Resolver |
| Cached-POM SCM lookup | Supported | No independent POM download |
| Parent-POM SCM inheritance | Supported | Bounded inheritance path |
| Direct public GitHub SCM normalization | Supported | Scorecard target |
| Apache GitBox to GitHub mirror normalization | Supported | Scorecard target |
| Current OpenSSF Scorecard lookup | Supported | Repository posture, not version provenance |
| Scorecard repo/date/commit capture | Supported | Stored as evidence attributes |
| Minimum current Scorecard score policy | Supported | Negative value disables threshold |
| Schema-versioned NDJSON | Supported | JSON serializer, stable field/order strategy |
| Concurrent evidence checks | Supported | Bounded by `parallelism` |
| Fail build on conclusive `FAIL` | Supported | Opt-in |
| Fail build on `UNKNOWN` | Supported | Opt-in |
| Skip execution | Supported | `supplyChainVerification.skip` |
| Custom report path | Supported | CLI/property configurable |
| SBOM schema/content validation | Not yet | Sidecar resolution is not content validation |
| Artifact-to-SBOM cryptographic binding | Not yet | No provenance claim |
| Artifact-version-to-source commit binding | Not yet | Scorecard is current repo posture |
| Vulnerability/CVE provider | Not yet | Deliberately not added on the old identity boundary |
| Attestation/provenance verification | Not yet | Roadmap |
| Non-GitHub Scorecard targets | Not yet | Current resolver targets GitHub-backed identities |
| Persistent cross-build evidence cache | Not yet | Roadmap |
| Public third-party provider SPI | Not yet | Internal boundary may still change |
| Maven Central publication of this plugin | Not yet | Build/install locally today |

## Evidence semantics

### PASS

The provider positively established the narrow claim named by that check.

For example, `sbom-sidecar-available: PASS` means a conventional sidecar is available in the build's Maven repository context. It does not establish public visibility outside that context, valid SBOM content, or artifact binding.

### FAIL

The provider obtained enough information to establish a negative answer for the narrow claim.

### UNKNOWN

The provider could not safely establish the answer. Examples include incomplete identity, artifact/POM provenance disagreement, offline remote Scorecard lookup, malformed or mismatched remote responses, and unsupported metadata.

### WARN

Reserved for non-blocking provider findings. Current providers do not rely on `WARN` for transport ambiguity.

## Compatibility

The build requires Maven 3.9+ and Java 17+. CI exercises Java 17 and 21. Broader matrix guarantees are not yet claimed.
