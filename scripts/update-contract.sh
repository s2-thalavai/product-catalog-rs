#!/usr/bin/env bash
# Regenerates the committed OpenAPI baseline used by OpenApiContractIT.
# Only run this for intended, backward-compatible API changes - then commit the result.
#   ./scripts/update-contract.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

mvn -B -ntp -q verify \
  -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false \
  -Dit.test=OpenApiContractIT -Djacoco.skip=true

mkdir -p src/test/resources/contract
cp target/contract/openapi.json src/test/resources/contract/openapi.json
echo "Baseline updated: src/test/resources/contract/openapi.json"
git --no-pager diff --stat -- src/test/resources/contract/ 2>/dev/null || true
