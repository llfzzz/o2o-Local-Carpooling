#!/usr/bin/env bash
set -euo pipefail

# Generates a local, gitignored .env for the demo profile with fresh random crypto secrets.
# Middleware credentials are the local-only demo values that match docker-compose.yml.
# Usage: scripts/generate-local-env.sh [--force]

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEMPLATE="$ROOT/.env.demo.example"
TARGET="$ROOT/.env"
FORCE="${1:-}"

if [[ ! -f "$TEMPLATE" ]]; then
  echo "error: $TEMPLATE not found" >&2
  exit 1
fi
if [[ -f "$TARGET" && "$FORCE" != "--force" ]]; then
  echo "refusing to overwrite existing .env (re-run with --force to regenerate)" >&2
  exit 1
fi
command -v openssl >/dev/null 2>&1 || { echo "error: openssl is required" >&2; exit 1; }

JWT_SECRET="$(openssl rand -base64 64 | tr -d '\n')"
FIELD_KEY="$(openssl rand -base64 32 | tr -d '\n')"
WEBHOOK_SECRET="$(openssl rand -base64 32 | tr -d '\n')"

# Replace only the three crypto placeholders; copy everything else verbatim.
awk \
  -v jwt="$JWT_SECRET" \
  -v field="$FIELD_KEY" \
  -v webhook="$WEBHOOK_SECRET" '
  /^JWT_BASE64_SECRET=/            { print "JWT_BASE64_SECRET=" jwt; next }
  /^FIELD_ENCRYPTION_KEY_BASE64=/  { print "FIELD_ENCRYPTION_KEY_BASE64=" field; next }
  /^PAYMENT_WEBHOOK_SECRET=/       { print "PAYMENT_WEBHOOK_SECRET=" webhook; next }
  { print }
' "$TEMPLATE" > "$TARGET"

chmod 600 "$TARGET"
echo "Wrote $TARGET (demo profile, fresh secrets, mode 600). It is gitignored."
echo "Next: docker compose up -d   then start the backend services and frontends."
