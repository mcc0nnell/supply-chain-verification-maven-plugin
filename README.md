# Supply Chain Verification Maven Plugin

Executable spike against [Maven Support & Care #224](https://github.com/support-and-care/maven-support-and-care/issues/224): expose public supply-chain evidence for the dependencies and build plugins a Maven build actually consumes.

**Current development version:** `0.4.0-SNAPSHOT`

This is deliberately not a vulnerability scanner. Existing tools already answer that problem. The narrow question here is different:

> Which consumed Maven coordinates have public evidence handles, including build plugins, in the same resolution context as the build?

The implementation is independent of Apache Maven and Maven Support & Care. The repository is intended to make the #224 implementation risk concrete; adoption, donation, package names, and governance remain separate decisions.

## What changed in 0.4

The evidence plane is Maven-native now.

- Maven Resolver resolves the component artifact, its POM, and Maven-hosted SBOM sidecars.
- Dependency evidence uses the project's effective remote repositories.
- Build-plugin evidence uses the project's effective plugin repositories.
- Maven's session controls mirrors, credentials, local repository, update policy, and offline mode.
- The report records the Resolver repository ID plus SHA-256 for the exact artifact and POM bytes Maven resolved.
- OpenSSF Scorecard lookup starts from SCM metadata in that Maven-resolved POM rather than re-fetching a POM from a hard-coded repository.

There is no second Maven repository HTTP client and no hard-coded Maven Central base URL in the verification path.

## Checks

### `public-sbom`

For each dependency and build plugin, the plugin asks Maven Resolver for these sidecar coordinates:

- `json:cyclonedx`
- `xml:cyclonedx`
- `json:spdx`

A resolved sidecar is `PASS`. A conclusive Resolver result that the supported sidecars are absent is `FAIL`. Transfer errors, inaccessible repositories, incomplete coordinates, and offline cache misses remain `UNKNOWN`.

That distinction is intentional: "not present" and "could not determine" are different facts.

The current slice verifies publication/discoverability only. It does **not** yet parse the SBOM and prove that its metadata describes the requested GAV.

### `openssf-scorecard`

The plugin parses SCM metadata from the Maven-resolved POM, follows parent-POM inheritance, canonicalizes supported GitHub and Apache GitBox references, and queries the public OpenSSF Scorecard API.

Scorecard evidence is explicitly **repository-level evidence**. A Scorecard result is not represented as certification of a Maven artifact or version.

When Maven is offline, the Scorecard API is never called. The observation is `UNKNOWN` with an explicit offline reason.

## Deterministic offline proof

The Maven Invoker integration test seeds an isolated local repository with:

- `org.example:fixture:1.0` JAR
- its POM with SCM metadata
- a CycloneDX JSON sidecar

Invoker then runs the fixture project with:

```text
mvn -o verify
```

The build proves three things in one run:

1. the dependency and POM are resolved from Maven's local repository;
2. the cached CycloneDX sidecar produces `PASS`;
3. Scorecard produces `UNKNOWN` without making a network request.

The generated evidence also records the SHA-256 of the artifact, POM, and resolved SBOM sidecar.

Run the full deterministic suite with:

```bash
mvn verify
```

## Live demo

Requirements: Java 17+ and Maven 3.9+.

```bash
./scripts/demo.sh
```

The demo uses its own `.demo-m2` local repository so stale permissions or tracking files in a developer's normal Maven cache do not affect the result. It installs the development plugin, then scans a project consuming `org.apache.commons:commons-lang3:3.17.0`.

The complete live report is written to:

```text
demo/target/supply-chain-verification.ndjson
```

## Configure it

```xml
<plugin>
  <groupId>io.github.mcc0nnell</groupId>
  <artifactId>supply-chain-verification-maven-plugin</artifactId>
  <version>0.4.0-SNAPSHOT</version>
  <configuration>
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
| `supplyChainVerification.requestTimeoutSeconds` | `5` | Timeout for the external Scorecard API |
| `supplyChainVerification.parallelism` | `8` | Maximum concurrent evidence checks |
| `supplyChainVerification.minimumScorecardScore` | `-1` | Optional repository-level Scorecard threshold; negative disables it |
| `supplyChainVerification.failOnFailure` | `false` | Reject the build when a check conclusively returns `FAIL` |
| `supplyChainVerification.failOnUnknown` | `false` | Reject the build when evidence cannot be determined |

Repository selection is not a plugin setting. Maven Resolver receives the effective project/plugin repositories and Maven session, so `settings.xml`, mirrors, credentials, local-repository selection, and `-o` stay authoritative.

## Evidence model

Every NDJSON observation has schema version `1` and records:

- full Maven coordinates plus component kind (`DEPENDENCY` or `BUILD_PLUGIN`);
- resolved artifact state, repository ID, and SHA-256;
- resolved POM state, repository ID, and SHA-256;
- check ID and `PASS`, `WARN`, `FAIL`, or `UNKNOWN`;
- a human-readable reason;
- logical or external evidence locations.

Example from the offline Invoker fixture:

```json
{"schemaVersion":1,"coordinates":"org.example:fixture:jar:1.0","gav":"org.example:fixture:1.0","kind":"DEPENDENCY","artifact":{"state":"FOUND","repositoryId":"local","sha256":"...","summary":"resolved by Maven"},"pom":{"state":"FOUND","repositoryId":"local","sha256":"...","summary":"resolved by Maven"},"check":"public-sbom","status":"PASS","summary":"CycloneDX JSON published in Maven resolution context","locations":["org.example:fixture:json:cyclonedx:1.0 [FOUND] maven:local:sha256:..."]}
```

## What this is not

This project does not claim to provide:

- CVE or vulnerability-database scanning;
- CRA compliance or legal compliance;
- artifact/version certification from an OpenSSF Scorecard result;
- SBOM content/GAV validation yet;
- provenance or attestation verification;
- universal Maven-coordinate-to-source-repository mapping;
- a replacement for Dependency-Check, OSS Index, CycloneDX, or repository-manager security products.

The policy layer is intentionally separate from observation. Teams can collect evidence without failing a build, or configure `FAIL`/`UNKNOWN` as gates.

## Next implementation slice

The next honest increment is evidence validation rather than breadth:

1. parse CycloneDX/SPDX and require the document to identify the consumed coordinate before returning `PASS`;
2. parse Scorecard JSON structurally and retain repository revision metadata;
3. add mirror/auth, multi-module, mismatch-sidecar, and policy cases to the Invoker matrix;
4. add a Maven Site/report surface while retaining versioned machine output.

CVE/OSV integration, a public provider SPI, attestations, and other scanner features are intentionally outside this slice.

## CI

CI runs on Java 17 and 21. Unit tests are deterministic, Maven Invoker proves the offline Resolver path, and the live demo checks that real Maven-hosted evidence still produces the expected report shape.

## License

Apache License 2.0.
