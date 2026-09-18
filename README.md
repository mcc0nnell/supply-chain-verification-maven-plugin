# Supply Chain Verification Maven Plugin

[![CI](https://github.com/mcc0nnell/supply-chain-verification-maven-plugin/actions/workflows/ci.yml/badge.svg)](https://github.com/mcc0nnell/supply-chain-verification-maven-plugin/actions/workflows/ci.yml)

A Maven-native evidence gate for the software a build actually resolves.

The plugin records the identity of resolved dependencies and build plugins, including the repository provenance Maven Resolver knows about and the SHA-256 of the consumed file. Evidence providers then make narrow, explicit claims about those resolved components and write deterministic NDJSON that can be reviewed or enforced in the Maven lifecycle.

It began as executable work toward [Maven Support & Care #224](https://github.com/support-and-care/maven-support-and-care/issues/224), whose initial scope calls for Maven-side checks for public SBOM and OpenSSF Scorecard evidence.

**Tagged source milestone:** `v0.3.0` — not published to Maven Central
**Development line:** `0.4.0-SNAPSHOT`  
**Requirements:** Java 17+, Maven 3.9+

## What changed in 0.4

The 0.4 line hardens the identity boundary found during adversarial testing:

- evidence is attached to the artifact Maven actually resolved, not just a GAV;
- reports include type, classifier, artifact SHA-256, POM SHA-256, and Maven Resolver repository provenance;
- SBOM sidecars are resolved through Maven Resolver using the artifact's source repository;
- Maven mirrors, repository authentication, proxying, local cache, checksum policy, and offline behavior therefore follow Maven rather than a parallel HTTP client;
- SCM/Scorecard association fails closed when Maven says the artifact and POM came from different repositories;
- Scorecard evidence is explicitly current repository posture, not artifact-version provenance;
- NDJSON uses a real JSON serializer and carries a schema version;
- integration tests use a deterministic local Maven repository and assert real PASS/FAIL behavior.

## Evidence semantics

- `PASS` — the specific claim named by the check was positively established.
- `WARN` — reserved for non-blocking provider findings.
- `FAIL` — the provider obtained a conclusive negative result for that claim.
- `UNKNOWN` — the provider could not safely establish the answer.

A timeout, repository ambiguity, offline remote lookup, or artifact/POM provenance mismatch therefore does not become a false negative or false positive.

## Current checks

### `public-sbom-sidecar`

Uses Maven Resolver to look for three conventional sidecar artifacts in the **same repository Maven resolved the consumed artifact from**:

- CycloneDX JSON: classifier `cyclonedx`, extension `json`
- CycloneDX XML: classifier `cyclonedx`, extension `xml`
- SPDX JSON: extension `spdx.json`

A resolved sidecar is `PASS` for the narrow claim **sidecar exists**.

It does **not** yet mean the SBOM is valid, describes the consumed artifact, or is cryptographically bound to the artifact. Those are follow-on checks.

### `openssf-scorecard-current`

Reads SCM metadata from the POM Maven actually cached for the resolved component, with bounded parent inheritance. Before using SCM metadata, the plugin requires the artifact and POM to have compatible Maven Resolver repository provenance.

The provider then canonicalizes supported GitHub/Apache GitBox references and queries the OpenSSF Scorecard API.

A `PASS` means:

> A current OpenSSF Scorecard result exists for the repository associated by the Maven-resolved POM.

It does **not** mean that Scorecard result describes the historical commit that produced the consumed artifact. The report records the Scorecard repository, observation date, and Scorecard commit and marks `artifactVersionBound=false`.

## Try the development line

The plugin is not yet published to Maven Central. Build and install it locally first:

```bash
git clone https://github.com/mcc0nnell/supply-chain-verification-maven-plugin.git
cd supply-chain-verification-maven-plugin
mvn -B -ntp clean install
```

Then configure:

```xml
<plugin>
  <groupId>io.github.mcc0nnell</groupId>
  <artifactId>supply-chain-verification-maven-plugin</artifactId>
  <version>0.4.0-SNAPSHOT</version>
  <executions>
    <execution>
      <phase>verify</phase>
      <goals>
        <goal>verify</goal>
      </goals>
    </execution>
  </executions>
  <configuration>
    <minimumScorecardScore>-1</minimumScorecardScore>
    <failOnFailure>false</failOnFailure>
    <failOnUnknown>false</failOnUnknown>
  </configuration>
</plugin>
```

The default report is:

```text
target/supply-chain-verification.ndjson
```

A 0.4 observation contains component identity and evidence separately:

```json
{"schemaVersion":1,"component":{"gav":"org.apache.commons:commons-lang3:3.17.0","type":"jar","classifier":null,"kind":"DEPENDENCY","artifactRepositoryId":"central","artifactRepositoryUrl":"https://repo.maven.apache.org/maven2","sha256":"..."},"check":"public-sbom-sidecar","status":"PASS","summary":"public SBOM sidecar resolved through Maven; content and artifact binding are not yet validated","attributes":{"claim":"sidecar-exists","contentValidated":"false","resolution":"maven-resolver"},"locations":["https://repo.maven.apache.org/maven2/org/apache/commons/commons-lang3/3.17.0/commons-lang3-3.17.0-cyclonedx.json"]}
```

## Configuration

| Property | Default | Meaning |
| --- | ---: | --- |
| `supplyChainVerification.skip` | `false` | Skip the goal entirely |
| `supplyChainVerification.reportFile` | `target/supply-chain-verification.ndjson` | Evidence output path |
| `supplyChainVerification.requestTimeoutSeconds` | `5` | Timeout for the fixed OpenSSF Scorecard endpoint |
| `supplyChainVerification.parallelism` | `8` | Maximum concurrent observations |
| `supplyChainVerification.minimumScorecardScore` | `-1` | Optional minimum current Scorecard score; negative disables the threshold |
| `supplyChainVerification.failOnFailure` | `false` | Reject the build on conclusive failures |
| `supplyChainVerification.failOnUnknown` | `false` | Reject the build when evidence is unresolved |

Repository selection is intentionally **not** a plugin parameter in 0.4. The plugin follows Maven Resolver's repository provenance.

## Development

```bash
# Unit tests and plugin packaging
mvn -B -ntp verify

# Maven Invoker test with a deterministic fixture repository
mvn -B -ntp -Prun-its verify

# Live Maven Central + OpenSSF smoke test
./scripts/demo.sh

# Generated Maven plugin/site documentation
mvn site
```

CI exercises Java 17 and 21. Live external-service validation remains separate from deterministic test gates.

## Project documentation

- [Current capabilities and explicit limits](docs/current-capabilities.md)
- [Architecture](docs/architecture.md)
- [Roadmap](ROADMAP.md)
- [Changelog](CHANGELOG.md)
- [Contributing](CONTRIBUTING.md)
- [Security policy](SECURITY.md)

## What this does not claim

The plugin does **not** yet claim:

- SBOM schema/content validation;
- artifact-to-SBOM cryptographic binding;
- artifact-to-source provenance;
- historical Scorecard posture for an artifact version;
- universal SCM resolution;
- vulnerability-provider coverage;
- attestation/provenance verification;
- persistent cross-build evidence caching.

Those are intentional follow-on slices behind a now-hardened resolved-artifact boundary.

## Origin

The repository was extracted from an Apache-2.0 engineering proof built in `mcc0nnell/scumm3` in August 2026.

This project is independent of the Apache Software Foundation and of Maven Support & Care. Apache Maven is a trademark of the Apache Software Foundation.

## License

Apache License 2.0.
