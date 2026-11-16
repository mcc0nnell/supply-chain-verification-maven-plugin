# CI-small execution route

GitHub is the canonical source. GitHub-hosted Actions are disabled in this fork to stop metered-run startup failures.

The private GitLab control project `robert526/supply-chain-verification-ci-control` receives authenticated GitHub `main` push webhooks. Its jobs are restricted to self-hosted runner `56236216` tagged `ci-small`; shared GitLab runners are disabled.

A source job checks repository identity, main branch, exact event SHA, the current GitHub main HEAD, and the cloned commit before distributing source files as artifacts.

Verification reproduces the former GitHub jobs:
- Java 17 Maven unit and Invoker verification with `mvn -B -ntp -Prun-its verify`.
- Java 21 Maven unit and Invoker verification using the same command.
- Java 21 live Maven Central/Scorecard demonstration, including evidence assertions for SBOM and Scorecard observations.

**Limitations:** Current CI-small triggering covers main-branch pushes only. PR-head tests and GitHub required-check status reporting are not yet wired. A successful main pipeline is not proof of an untested PR head. GitLab CI jobs are the evidence source; skipped or missing jobs never count as passing.

Never commit webhook tokens or CI credentials into this source repository.
