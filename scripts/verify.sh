#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUNDLED_BIN="/Users/llfzzz/.cache/codex-runtimes/codex-primary-runtime/dependencies/bin"
BUNDLED_NODE_BIN="/Users/llfzzz/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin"

if ! command -v pnpm >/dev/null 2>&1 && [ -x "${BUNDLED_BIN}/pnpm" ]; then
  export PATH="${BUNDLED_NODE_BIN}:${BUNDLED_BIN}:${PATH}"
fi

cd "${ROOT_DIR}"

./mvnw test -DskipTests=false

pnpm install --frozen-lockfile
pnpm typecheck
pnpm build
