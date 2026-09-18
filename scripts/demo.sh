#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
mvn -B -ntp clean install
mvn -B -ntp -f demo/pom.xml clean verify
echo
echo "== deterministic evidence =="
cat demo/target/supply-chain-verification.ndjson
