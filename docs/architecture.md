# Architecture

## Design goal

The plugin turns remote supply-chain facts into deterministic Maven-build observations without collapsing uncertainty into false certainty.

```text
MavenProject
   |
   +-- dependencies
   +-- build plugins
          |
          v
     Coordinate list
          |
          +-------------------+
          |                   |
          v                   v
   public-sbom         openssf-scorecard
          |                   |
          +---------+---------+
                    |
                    v
             Evidence records
          PASS/WARN/FAIL/UNKNOWN
                    |
                    v
       deterministic NDJSON report
                    |
                    v
             optional policy gate
```

## Component discovery

`VerifyMojo` reads the resolved project dependencies and declared build plugins, converts them into a common coordinate model, and sorts them before observation output.

Checks may execute concurrently. Output ordering remains component/check deterministic because observations are collected in submission order.

## Evidence providers

`EvidenceCheck` is currently an internal boundary, not a supported external SPI.

Each provider receives one Maven coordinate and returns:

- a stable check identifier;
- one status;
- a concise summary;
- zero or more evidence/candidate locations.

Providers are responsible for distinguishing a conclusive negative result from an unavailable or ambiguous evidence channel.

### Public SBOM provider

The SBOM provider derives conventional artifact-adjacent locations from a repository base URI and probes them with bounded HTTP requests.

### OpenSSF Scorecard provider

The Scorecard provider resolves SCM metadata from the published POM chain, canonicalizes supported repository forms, and then queries the OpenSSF Scorecard API.

SCM resolution and Scorecard retrieval are intentionally separate steps because they have different failure semantics.

## Policy

Reporting is always produced before policy enforcement.

`failOnFailure` rejects a build only when at least one provider returned `FAIL`.

`failOnUnknown` lets stricter environments reject unresolved evidence without mislabeling it as a failed check.

## Network boundary

Remote Maven repositories, published POMs, redirects, and API responses are untrusted inputs. All network operations are bounded by the configured timeout.

The project currently has no persistent cache. Cache freshness and offline semantics are roadmap items rather than implicit behavior.

## Testing strategy

- Unit tests inject probes/fetchers and exercise provider semantics deterministically.
- Maven Invoker tests execute the packaged plugin inside an actual Maven build.
- Live smoke tests reach Maven Central and OpenSSF separately from deterministic CI.
