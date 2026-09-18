# Supply Chain Verification Maven Plugin

[![CI](https://github.com/mcc0nnell/supply-chain-verification-maven-plugin/actions/workflows/ci.yml/badge.svg)](https://github.com/mcc0nnell/supply-chain-verification-maven-plugin/actions/workflows/ci.yml)

A Maven-native evidence gate for the software you consume.

The plugin inspects project dependencies and build plugins, asks independent evidence providers what is publicly verifiable about each component, and writes deterministic NDJSON that can be reviewed or enforced in the Maven lifecycle.

It began as executable work toward [Maven Support & Care #224](https://github.com/support-and-care/maven-support-and-care/issues/224), whose initial scope calls for Maven-side verification of public SBOMs and OpenSSF Scorecard data.

**Released:** `0.3.0`  
**Development line:** `0.4.0-SNAPSHOT`  
**Requirements:** Java 17+, Maven 3.9+

## Why this exists

Vulnerability scanning answers an important question, but not the only one. A consumer may also need to know whether an upstream component publishes an SBOM, whether a public project-health signal exists, and whether missing evidence should warn or fail the build.

This plugin keeps those observations distinct:

- `PASS` — the requested evidence was positively verified.
- `WARN` — reserved for non-blocking provider findings.
- `FAIL` — the provider reached a conclusive negative result.
- `UNKNOWN` — the provider could not establish the answer.

A timeout is therefore not reported as “no SBOM,” and ambiguous SCM metadata is not reported as “no Scorecard.”

## Current checks

### `public-sbom`

Checks conventional Maven repository publication locations for:

- CycloneDX JSON
- CycloneDX XML
- SPDX JSON

HTTP `2xx` is `PASS`; exhausting known locations with `404`/`410` is `FAIL`; transient or ambiguous responses remain `UNKNOWN`.

### `openssf-scorecard`

Reads SCM metadata from the component's published POM, follows parent-POM inheritance when necessary, canonicalizes supported GitHub and Apache GitBox references, and queries the OpenSSF Scorecard API.

A published score is `PASS` by default. Configure `minimumScorecardScore` to turn a score below your policy floor into `FAIL`.

## Use it

Released configuration:

```xml
<plugin>
  <groupId>io.github.mcc0nnell</groupId>
  <artifactId>supply-chain-verification-maven-plugin</artifactId>
  <version>0.3.0</version>
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

Example observation:

```json
{"gav":"org.apache.commons:commons-lang3:3.17.0","kind":"DEPENDENCY","check":"public-sbom","status":"PASS","summary":"public SBOM published","locations":["https://repo.maven.apache.org/maven2/org/apache/commons/commons-lang3/3.17.0/commons-lang3-3.17.0-cyclonedx.json"]}
```

## Configuration

| Property | Default | Meaning |
| --- | ---: | --- |
| `supplyChainVerification.skip` | `false` | Skip the goal entirely |
| `supplyChainVerification.reportFile` | `target/supply-chain-verification.ndjson` | Evidence output path |
| `supplyChainVerification.repositoryUrl` | Maven Central | Repository used for public SBOM and POM discovery |
| `supplyChainVerification.requestTimeoutSeconds` | `5` | Connect/request timeout |
| `supplyChainVerification.parallelism` | `8` | Maximum concurrent observations |
| `supplyChainVerification.minimumScorecardScore` | `-1` | Optional minimum Scorecard score; negative disables the threshold |
| `supplyChainVerification.failOnFailure` | `false` | Reject the build on conclusive failures |
| `supplyChainVerification.failOnUnknown` | `false` | Reject the build when evidence is unresolved |

Command-line properties use normal Maven `-D` syntax:

```bash
mvn verify \
  -DsupplyChainVerification.minimumScorecardScore=7.0 \
  -DsupplyChainVerification.failOnFailure=true
```

## Development

The project deliberately separates deterministic build confidence from live external-system smoke testing.

```bash
# Unit tests and plugin packaging
mvn -B -ntp verify

# Apache-style Maven Invoker integration test
mvn -B -ntp -Prun-its verify

# Live Maven Central + OpenSSF smoke test
./scripts/demo.sh

# Generated Maven plugin/site documentation
mvn site
```

CI runs the regular build and Invoker test on Java 17 and 21. The live smoke test has its own workflow because Maven Central and the OpenSSF API are external dependencies, not hermetic build inputs.

## Project documentation

- [Current capabilities and explicit limits](docs/current-capabilities.md)
- [Architecture](docs/architecture.md)
- [Roadmap](ROADMAP.md)
- [Changelog](CHANGELOG.md)
- [Contributing](CONTRIBUTING.md)
- [Security policy](SECURITY.md)

## What this does not claim

The plugin currently verifies public discoverability and selected project-health evidence. It does **not** yet claim SBOM schema/content validation, artifact-to-source provenance, universal SCM resolution, non-GitHub Scorecard targets, vulnerability-provider coverage, attestation verification, or production cache/offline policy.

Those are intentional follow-on slices behind the evidence-provider boundary; see [ROADMAP.md](ROADMAP.md).

## Origin

The repository was extracted from an Apache-2.0 engineering proof built in `mcc0nnell/scumm3` in August 2026. The standalone line then added real Maven repository SBOM discovery and live OpenSSF Scorecard verification.

This project is independent of the Apache Software Foundation and of Maven Support & Care. Apache Maven is a trademark of the Apache Software Foundation.

## License

Apache License 2.0.
