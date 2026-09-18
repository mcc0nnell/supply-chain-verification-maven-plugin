# Supply Chain Verification Maven Plugin

Executable work toward [Maven Support & Care #224](https://github.com/support-and-care/maven-support-and-care/issues/224): make dependency and build-plugin supply-chain evidence visible inside the Maven build that consumes them.

**Current release:** `0.2.0`

The plugin is intentionally small. Maven coordinates go through independent `EvidenceCheck` providers and produce deterministic NDJSON that policy can report on or reject.

## What works now

`public-sbom` performs real remote discovery against a Maven repository. For every dependency and build plugin it checks the conventional publication locations for:

- CycloneDX JSON
- CycloneDX XML
- SPDX JSON

A successful HTTP response produces `PASS`. Exhausting every known location with `404`/`410` produces `FAIL`. Network failures, transient server responses, and incomplete coordinates stay `UNKNOWN` rather than being misreported as absence.

The current `openssf-scorecard` provider remains explicitly `UNKNOWN` until a Maven coordinate can be mapped reliably to its canonical source repository.

## Run the demo

Requirements: Java 17+ and Maven 3.9+.

```bash
./scripts/demo.sh
```

The demo scans a real project containing `org.apache.commons:commons-lang3:3.17.0`. Maven Central publishes a CycloneDX SBOM for that artifact, so the report contains a real observation like:

```json
{"gav":"org.apache.commons:commons-lang3:3.17.0","kind":"DEPENDENCY","check":"public-sbom","status":"PASS","summary":"public SBOM published","locations":["https://repo.maven.apache.org/maven2/org/apache/commons/commons-lang3/3.17.0/commons-lang3-3.17.0-cyclonedx.json"]}
```

The complete report is written to:

```
demo/target/supply-chain-verification.ndjson
```

Checks run concurrently, while observations are written in deterministic component/check order.

## Configure it

```xml
<plugin>
  <groupId>io.github.mcc0nnell</groupId>
  <artifactId>supply-chain-verification-maven-plugin</artifactId>
  <version>0.2.0</version>
  <configuration>
    <repositoryUrl>https://repo.maven.apache.org/maven2</repositoryUrl>
    <requestTimeoutSeconds>5</requestTimeoutSeconds>
    <parallelism>8</parallelism>
    <failOnFailure>false</failOnFailure>
    <failOnUnknown>false</failOnUnknown>
  </configuration>
  <executions>
    <execution>
      <phase>verify</phase>
      <goals><goal>verify</goal></goals>
    </execution>
  </executions>
</plugin>
```

| Property | Default | Meaning |
| --- | ---: | --- |
| `supplyChainVerification.repositoryUrl` | Maven Central | Repository used for public SBOM discovery |
| `supplyChainVerification.requestTimeoutSeconds` | `5` | Connect/request timeout |
| `supplyChainVerification.parallelism` | `8` | Maximum concurrent evidence checks |
| `supplyChainVerification.failOnFailure` | `false` | Reject the build when a check conclusively returns `FAIL` |
| `supplyChainVerification.failOnUnknown` | `false` | Reject the build when evidence cannot be resolved |

Command-line properties use the `-D` form, for example:

```bash
mvn verify -DsupplyChainVerification.failOnFailure=true
```

## Evidence model

Each line records:

- Maven GAV
- component kind (`DEPENDENCY` or `BUILD_PLUGIN`)
- check identifier
- `PASS`, `WARN`, `FAIL`, or `UNKNOWN`
- a short reason
- evidence/candidate locations

That distinction matters: a repository timeout is not the same fact as a missing SBOM.

## Deliberately not claimed yet

The current remote SBOM check establishes **public discoverability**, not semantic validity of the document. The project does not yet claim:

- schema/content validation of retrieved SBOMs
- canonical Maven-coordinate → SCM repository resolution
- live OpenSSF Scorecard retrieval or score policy
- CVE/vulnerability providers
- provenance or attestation verification
- production cache/offline/rate-limit policy
- publication to Maven Central

Those are separate implementation slices behind the same `EvidenceCheck` boundary.

## CI and compatibility

CI builds and runs the end-to-end demo on Java 17 and Java 21. Unit tests keep network semantics deterministic by injecting a probe rather than depending on live infrastructure; the demo supplies the live Maven Central integration path.

## Origin

This repository was extracted from an Apache-2.0 engineering proof originally built in `mcc0nnell/scumm3` on 2026-08-24. The original proof caught and rejected a Java 17 compatibility mistake before accepting the corrected build.

The first standalone release, `v0.1.0`, established the plugin boundary, deterministic evidence format, demo, CI, and release mechanics. `0.2.0` adds the first real evidence provider.

## License

Apache License 2.0.
