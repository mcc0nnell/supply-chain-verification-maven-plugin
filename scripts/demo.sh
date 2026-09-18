#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MAVEN_REPO="${DEMO_MAVEN_REPO:-$ROOT/.demo-m2}"

mkdir -p "$MAVEN_REPO"
cd "$ROOT"

mvn -B -ntp -Dmaven.repo.local="$MAVEN_REPO" clean install
mvn -B -ntp -Dmaven.repo.local="$MAVEN_REPO" -f demo/pom.xml clean verify

echo
echo "== deterministic evidence =="
cat demo/target/supply-chain-verification.ndjson
