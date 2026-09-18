# Current Capabilities

This document is the authoritative “what works now” boundary for the development line.

| Capability | Status | Notes |
| --- | --- | --- |
| Enumerate resolved Maven dependencies | Supported | Uses the current Maven project |
| Enumerate build plugins | Supported | Reported as a distinct component kind |
| CycloneDX JSON publication discovery | Supported | Conventional Maven repository location |
| CycloneDX XML publication discovery | Supported | Conventional Maven repository location |
| SPDX JSON publication discovery | Supported | Conventional Maven repository location |
| Published-POM SCM lookup | Supported | Used by Scorecard resolution |
| Parent-POM SCM inheritance | Supported | Bounded inheritance path |
| Direct public GitHub SCM normalization | Supported | Scorecard target |
| Apache GitBox to GitHub mirror normalization | Supported | Scorecard target |
| OpenSSF Scorecard lookup | Supported | Public API |
| Minimum Scorecard score policy | Supported | Negative value disables threshold |
| Deterministic NDJSON report | Supported | Stable component/check order |
| Concurrent evidence checks | Supported | Bounded by `parallelism` |
| Fail build on conclusive `FAIL` | Supported | Opt-in |
| Fail build on `UNKNOWN` | Supported | Opt-in |
| Skip execution | Supported in 0.4 dev | `supplyChainVerification.skip` |
| Custom report path | Supported in 0.4 dev | CLI/property configurable |
| SBOM schema validation | Not yet | Discovery is not content validation |
| Artifact-to-SBOM cryptographic binding | Not yet | No provenance claim |
| Vulnerability/CVE provider | Not yet | Deliberately separate from current checks |
| Attestation/provenance verification | Not yet | Roadmap |
| Non-GitHub Scorecard targets | Not yet | Current resolver targets GitHub-backed Scorecard identities |
| Persistent cache/offline mode | Not yet | Roadmap |
| Public third-party provider SPI | Not yet | Internal boundary may still change |
| Maven Central publication of this plugin | Not yet | Source builds/install locally today |

## Evidence semantics

### PASS

The provider positively verified the requested evidence.

### FAIL

The provider obtained enough information to establish a negative answer, such as exhausting known SBOM locations with `404`/`410`, or receiving no published Scorecard result for a resolved repository.

### UNKNOWN

The evidence channel could not establish the answer. Examples include incomplete coordinates, ambiguous SCM metadata, timeouts, transient HTTP failures, and malformed remote responses.

### WARN

Reserved for non-blocking provider findings. Current providers do not rely on `WARN` for transport ambiguity.

## Compatibility

The build requires Maven 3.9+ and Java 17+. CI exercises Java 17 and 21. Broader matrix guarantees are not yet claimed.
