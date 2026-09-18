# Supply Chain Verification Maven Plugin

A runnable architecture spike for [Maven Support & Care #224](https://github.com/support-and-care/maven-support-and-care/issues/224): make dependency and build-plugin supply-chain evidence visible inside a Maven build, with deterministic output and explicit policy.

**Status:** v0.1.0 is an engineering spike, not a production security gate.

## Run it

Requirements: Java 17+ and Maven 3.9+.

```bash
./scripts/demo.sh
```

The demo builds the plugin, runs it against a small Maven project, and writes:

```
demo/target/supply-chain-verification.ndjson
```

Each component receives two observations today:

- `public-sbom` — deterministic candidate CycloneDX/SPDX locations in Maven Central
- `openssf-scorecard` — an explicit unresolved observation until Maven coordinates can be mapped to canonical source repositories

Set `supplyChainVerification.failOnUnknown=true` to make unresolved evidence fail the build.

## What this proves

- dependencies and build plugins are both enumerated
- checks plug in through a small `EvidenceCheck` seam
- observations are sorted and emitted as deterministic NDJSON
- policy can distinguish reporting from fail-closed enforcement
- CI exercises Java 17 and Java 21

## Deliberately not claimed yet

This release does **not** fetch or validate remote SBOMs, resolve canonical SCM repositories, call OpenSSF Scorecard, query CVE databases, verify attestations, or define production cache/offline/rate-limit policy. Those are the next implementation slices.

The point of this release is to turn #224 from a design discussion into executable code with a narrow, inspectable boundary.

## Coordinates

```xml
<plugin>
  <groupId>io.github.mcc0nnell</groupId>
  <artifactId>supply-chain-verification-maven-plugin</artifactId>
  <version>0.1.0</version>
</plugin>
```

v0.1.0 is distributed as a GitHub release artifact; it is not yet published to Maven Central.

## Origin

This standalone repository was extracted from an Apache-2.0 engineering proof originally built in `mcc0nnell/scumm3` on 2026-08-24. The original proof caught and rejected a Java 17 compatibility mistake before accepting the corrected build.

## License

Apache License 2.0.
