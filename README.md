# Supply Chain Verification Maven Plugin

Executable work toward [Maven Support & Care #224](https://github.com/support-and-care/maven-support-and-care/issues/224): make dependency and build-plugin supply-chain evidence visible inside the Maven build that consumes them.

**Current release:** `0.3.0`

The plugin is intentionally small. Maven coordinates go through independent `EvidenceCheck` providers and produce deterministic NDJSON that policy can report on or reject.

## What works now

`public-sbom` performs real remote discovery against a Maven repository. For every dependency and build plugin it checks the conventional publication locations for:

- CycloneDX JSON
- CycloneDX XML
- SPDX JSON

A successful HTTP response produces `PASS`. Exhausting every known location with `404`/`410` produces `FAIL`. Network failures, transient server responses, and incomplete coordinates stay `UNKNOWN` rather than being misreported as absence.

`openssf-scorecard` is now live too. It reads SCM metadata from the component's published POM, follows parent POM inheritance when the child omits SCM, canonicalizes supported public GitHub and Apache GitBox references, and queries the OpenSSF Scorecard API. A retrieved score produces `PASS` by default; a missing public Scorecard result produces `FAIL`; ambiguous SCM or transient network/API failures remain `UNKNOWN`.

Set `minimumScorecardScore` to make a retrieved score below your chosen threshold return `FAIL`. The default is `-1`, which disables score-threshold enforcement while still verifying that Scorecard data is publicly available.

## Run the demo

Requirements: Java 17+ and Maven 3.9+.

```bash
./scripts/demo.sh
```

The demo scans a real project containing `org.apache.commons:commons-lang3:3.17.0`. Maven Central publishes a CycloneDX SBOM for that artifact, so the report contains a real observation like:

```json
{"gav":"org.apache.commons:commons-lang3:3.17.0","kind":"DEPENDENCY","check":"public-sbom","status":"PASS","summary":"public SBOM published","locations":["https://repo.maven.apache.org/maven2/org/apache/commons/commons-lang3/3.17.0/commons-lang3-3.17.0-cyclonedx.json"]}
```

The same run resolves Commons Lang's Apache GitBox SCM metadata to its GitHub mirror and retrieves the live OpenSSF Scorecard result:

```json
{"gav":"org.apache.commons:commons-lang3:3.17.0","kind":"DEPENDENCY","check":"openssf-scorecard","status":"PASS","summary":"OpenSSF Scorecard 8.1 (2026-09-15T16:58:50Z)","locations":["https://repo.maven.apache.org/maven2/org/apache/commons/commons-lang3/3.17.0/commons-lang3-3.17.0.pom","https://gitbox.apache.org/repos/asf?p=commons-lang.git","https://github.com/apache/commons-lang","https://api.securityscorecards.dev/projects/github.com/apache/commons-lang"]}
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
  <version>0.3.0</version>
  <configuration>
    <repositoryUrl>https://repo.maven.apache.org/maven2</repositoryUrl>
    <requestTimeoutSeconds>5</requestTimeoutSeconds>
    <parallelism>8</parallelism>
    <minimumScorecardScore>-1</minimumScorecardScore>
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
| `supplyChainVerification.minimumScorecardScore` | `-1` | Optional minimum OpenSSF Scorecard score; negative disables threshold enforcement |
| `supplyChainVerification.failOnFailure` | `false` | Reject the build when a check conclusively returns `FAIL` |
| `supplyChainVerification.failOnUnknown` | `false` | Reject the build when evidence cannot be resolved |

Command-line properties use the `-D` form, for example:

```bash
mvn verify \
  -DsupplyChainVerification.minimumScorecardScore=7.0 \
  -DsupplyChainVerification.failOnFailure=true
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

The current checks establish **public discoverability and live Scorecard retrieval**, not complete supply-chain truth. The project does not yet claim:

- schema/content validation of retrieved SBOMs
- universal Maven-coordinate → SCM resolution; v0.3.0 supports direct public GitHub SCM metadata, Apache GitBox → GitHub mirrors, and parent-POM inheritance
- equivalence between a Maven artifact and whatever repository its published POM declares beyond that metadata chain
- non-GitHub Scorecard targets
- CVE/vulnerability providers
- provenance or attestation verification
- production cache/offline/rate-limit policy
- publication to Maven Central

Those are separate implementation slices behind the same `EvidenceCheck` boundary.

## CI and compatibility

CI builds and runs the end-to-end demo on Java 17 and Java 21. Unit tests keep network semantics deterministic by injecting a probe rather than depending on live infrastructure; the demo supplies the live Maven Central integration path.

## Origin

This repository was extracted from an Apache-2.0 engineering proof originally built in `mcc0nnell/scumm3` on 2026-08-24. The original proof caught and rejected a Java 17 compatibility mistake before accepting the corrected build.

The first standalone release, `v0.1.0`, established the plugin boundary, deterministic evidence format, demo, CI, and release mechanics. `v0.2.0` added live Maven repository SBOM discovery. `v0.3.0` adds published-POM SCM resolution and live OpenSSF Scorecard retrieval.

## License

Apache License 2.0.
