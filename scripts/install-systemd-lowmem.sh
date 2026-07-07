#!/usr/bin/env bash
# Install and start the O2O backend as low-memory systemd services.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [[ ! -f .env ]]; then
  echo "error: .env not found - run scripts/generate-local-env.sh first" >&2
  exit 1
fi

install -m 0644 deploy/systemd/o2o@.service /etc/systemd/system/o2o@.service
systemctl daemon-reload
systemctl reset-failed 'o2o@*.service' || true

port_for() {
  case "$1" in
    auth-service) echo 8101 ;;
    user-service) echo 8102 ;;
    driver-service) echo 8103 ;;
    trip-service) echo 8104 ;;
    order-service) echo 8105 ;;
    payment-sim-service) echo 8106 ;;
    map-service) echo 8107 ;;
    file-service) echo 8108 ;;
    ai-service) echo 8109 ;;
    admin-service) echo 8110 ;;
    audit-service) echo 8111 ;;
    notification-service) echo 8112 ;;
    identity-service) echo 8113 ;;
    gateway-service) echo 8120 ;;
    *) return 1 ;;
  esac
}

wait_for_health() {
  local svc="$1"
  local unit="o2o@$svc.service"
  local port
  port="$(port_for "$svc")"
  local deadline=$((SECONDS + ${O2O_HEALTH_TIMEOUT:-240}))

  until curl -m 60 -fsS "http://127.0.0.1:$port/actuator/health" >/dev/null; do
    if ! systemctl is-active --quiet "$unit"; then
      journalctl -u "$unit" -n 80 --no-pager >&2
      return 1
    fi
    if (( SECONDS >= deadline )); then
      echo "error: $unit did not become healthy on port $port" >&2
      journalctl -u "$unit" -n 80 --no-pager >&2
      return 1
    fi
    sleep 5
  done
}

SERVICES=(
  auth-service
  user-service
  driver-service
  trip-service
  order-service
  payment-sim-service
  map-service
  file-service
  ai-service
  admin-service
  audit-service
  notification-service
  identity-service
  gateway-service
)

delay="${O2O_START_DELAY:-6}"
for svc in "${SERVICES[@]}"; do
  port="$(port_for "$svc")"
  if systemctl is-active --quiet "o2o@$svc.service" &&
    curl -m 60 -fsS "http://127.0.0.1:$port/actuator/health" >/dev/null; then
    echo "already healthy o2o@$svc.service"
    continue
  fi
  systemctl enable "o2o@$svc.service" >/dev/null
  systemctl restart "o2o@$svc.service"
  wait_for_health "$svc"
  echo "healthy o2o@$svc.service"
  sleep "$delay"
done
