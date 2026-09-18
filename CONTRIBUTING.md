# Contributing

Thanks for helping improve the Supply Chain Verification Maven Plugin.

The project aims to behave like a small, well-governed Maven component: focused changes, deterministic evidence, explicit failure semantics, and integration tests that execute the plugin through Maven rather than only unit-testing helper classes.

## Prerequisites

- JDK 17 or newer
- Maven 3.9 or newer

## Build gates

Run these before opening a pull request:

```bash
mvn -B -ntp verify
mvn -B -ntp -Prun-its verify
```

For changes to remote evidence behavior, also run the live smoke test:

```bash
./scripts/demo.sh
```

The live smoke test reaches Maven Central and the OpenSSF Scorecard API. Unit and integration tests should not depend on those services for their assertions.

## Development rules

- Keep each pull request to one logical change.
- Add or update tests for behavior changes.
- Preserve the distinction between `FAIL` and `UNKNOWN`. Network or metadata ambiguity must not become a conclusive negative claim.
- Keep report ordering deterministic even when checks execute concurrently.
- Treat Maven coordinates, published POM metadata, and remote API data as untrusted input.
- Do not add a new network dependency without documenting its timeout, error semantics, and evidence boundary.
- User-visible changes belong in `CHANGELOG.md` under `Unreleased`.
- If behavior changes a documented capability or limitation, update `docs/current-capabilities.md` in the same pull request.

## Test layers

**Unit tests** exercise provider semantics with injected probes/fetchers.

**Invoker tests** under `src/it/` prove that the packaged plugin executes inside a real Maven build. They should use bounded/local failure cases when possible rather than relying on public services.

**Live smoke tests** prove selected public integrations still work. They are intentionally separated from deterministic CI.

## Commit and pull-request style

Use a short imperative subject and explain the evidence for the change in the body. The pull-request template asks for scope, verification, and evidence-boundary changes.

## Security issues

Do not open a public issue for a vulnerability. Follow [SECURITY.md](SECURITY.md).
