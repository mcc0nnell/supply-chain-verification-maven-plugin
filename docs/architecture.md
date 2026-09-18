# Architecture

## Design goal

The plugin turns narrowly defined supply-chain observations into Maven-build evidence without detaching those observations from the artifact the build actually consumed.

```text
MavenProject / MavenSession
        |
        +-- resolved dependencies
        +-- resolved build plugins
        |
        v
Maven Resolver provenance + local files
        |
        +-- artifact repository id / URL
        +-- POM repository id / URL
        +-- artifact SHA-256
        +-- POM SHA-256
        +-- type / classifier / extension
        |
        v
   ResolvedComponent
        |
        +--------------------------+
        |                          |
        v                          v
sbom-sidecar-available      openssf-scorecard-current
(Maven Resolver)          (local POM -> fixed API)
        |                          |
        +------------+-------------+
                     |
                     v
              Evidence records
           PASS/WARN/FAIL/UNKNOWN
                     |
                     v
          schema-versioned NDJSON
                     |
                     v
              optional policy gate
```

## Resolved-component boundary

A GAV is not sufficient identity.

`VerifyMojo` captures the artifact Maven actually resolved, including type, classifier, local file SHA-256, and the repository provenance reported by Maven Resolver's local repository manager.

The corresponding cached POM is resolved independently through the same Resolver provenance API and receives its own repository id and SHA-256.

This matters because Maven can legally end up with a JAR and POM of the same GAV from different repositories. In that case the plugin must not silently apply the POM's SCM identity to the consumed JAR.

## SBOM sidecar availability provider

`sbom-sidecar-available` uses Maven Resolver, not a parallel HTTP client.

For the repository Maven says supplied the artifact, it requests conventional Maven artifacts representing:

- CycloneDX JSON;
- CycloneDX XML;
- SPDX JSON.

This means mirrors, authentication, proxy configuration, checksum policy, local cache, and offline state are Maven concerns rather than reimplemented plugin concerns.

The current claim is deliberately narrow: **a conventional sidecar artifact is available in the build's Maven repository context**.

The provider records `visibility=repository-context`; it does not infer public reachability from repository access. Public-visibility verification, content validation, and artifact binding are separate concerns.

## OpenSSF Scorecard provider

The Scorecard path begins with the POM already present in Maven's local repository.

Before trusting project-level SCM metadata, the top-level component requires compatible repository provenance for the consumed artifact and its POM. A disagreement returns `UNKNOWN`.

SCM parsing only accepts direct project-level `<scm>` metadata; nested elements elsewhere in the POM do not count.

Supported SCM forms are canonicalized to a GitHub-backed Scorecard project. The fixed OpenSSF API response is parsed structurally and records:

- current numeric score;
- observation date;
- repository identity;
- Scorecard repository commit.

The provider explicitly labels this **current repository posture** and records `artifactVersionBound=false`.

## Report contract

NDJSON is emitted with a real JSON generator and `schemaVersion: 1`.

Each observation carries component identity separately from provider evidence. Untrusted strings therefore cannot inject physical NDJSON lines through unescaped control characters.

Component observations are sorted deterministically before concurrent inspection. Provider execution may be concurrent, but result collection follows submission order.

Live remote values can of course change between builds; deterministic refers to serialization and ordering for the same observed inputs, not to freezing external reality.

## Policy

Reporting is written before policy enforcement.

`failOnFailure` rejects a build when at least one provider returned `FAIL`.

`failOnUnknown` lets stricter environments reject unresolved evidence without mislabeling uncertainty as failure.

## Network boundary

SBOM sidecars are fetched by Maven Resolver from the repository already associated with the resolved artifact.

The only provider-owned remote endpoint in the current design is the fixed OpenSSF Scorecard API. Redirect following is disabled, responses are size-bounded, and malformed/mismatched responses become `UNKNOWN`.

Repository URLs written to reports have URI user-info stripped.

## Testing strategy

- Unit tests inject provider boundaries and exercise evidence semantics.
- Maven Invoker tests resolve fixture artifacts from a deterministic local Maven repository and assert repository provenance, SHA-256, PASS/FAIL behavior, and absence of Central substitution.
- Adversarial regression tests cover nested-SCM confusion and NDJSON control-character handling.
- Live smoke tests validate selected Maven Central and OpenSSF integrations separately from deterministic CI.
