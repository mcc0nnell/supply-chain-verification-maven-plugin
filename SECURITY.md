# Security Policy

## Supported versions

Security fixes are applied to the current development line and, when practical, the most recent released line.

| Version | Supported |
| --- | --- |
| 0.4.x development | Yes |
| 0.3.x | Yes |
| < 0.3 | Best effort |

## Reporting a vulnerability

Please do not open a public issue for a vulnerability.

Prefer GitHub's private security-advisory / private vulnerability-reporting flow for this repository when it is available. If that path is unavailable, contact the maintainer at `robert@mcc0nnell.org` with:

- affected version or commit;
- a minimal reproducer;
- expected and observed behavior;
- whether exploitation requires a malicious POM, repository, API response, or local Maven configuration;
- any suggested mitigation.

Please avoid including secrets or third-party private data in a report.

## Security model

The plugin processes Maven project metadata and data retrieved from remote repositories and APIs. Remote content is untrusted. A provider should fail to `UNKNOWN` when the evidence channel is unavailable or ambiguous rather than manufacturing a conclusive negative result.

The project does not currently claim sandboxing of Maven itself, artifact provenance verification, or authenticity of remote evidence beyond the transport and metadata paths documented in [docs/current-capabilities.md](docs/current-capabilities.md).
