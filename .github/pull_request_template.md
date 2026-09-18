## What changed

Describe the smallest logical change in this pull request.

## Why

What problem or evidence gap does it address?

## Verification

- [ ] `mvn -B -ntp verify`
- [ ] `mvn -B -ntp -Prun-its verify`
- [ ] Live smoke test run when remote evidence behavior changed
- [ ] Documentation updated when capability/limit semantics changed
- [ ] `CHANGELOG.md` updated for user-visible changes

## Evidence boundary

Does this change alter what the plugin can conclude as `PASS`, `FAIL`, `WARN`, or `UNKNOWN`? If yes, explain the before/after boundary explicitly.
