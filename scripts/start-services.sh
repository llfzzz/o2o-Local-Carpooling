#!/usr/bin/env bash
# Launch all 14 backend services on the host against the Docker middleware (demo profile).
# Prereqs: `docker compose up -d` healthy, and `./mvnw package -DskipTests` has produced fat jars.
# Logs go to ${O2O_LOG_DIR:-/tmp/o2o-logs}. Stop everything with: pkill -f '0.5.0-SNAPSHOT.jar'
set -u
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
LOG_DIR="${O2O_LOG_DIR:-/tmp/o2o-logs}"
mkdir -p "$LOG_DIR"

if [[ ! -f .env ]]; then
  echo "error: .env not found — run scripts/generate-local-env.sh first" >&2
  exit 1
fi
# Load .env safely: values may contain & ? @, so never `source` it.
while IFS= read -r line; do
  case "$line" in ''|\#*) continue;; esac
  export "$line"
done < .env
export SPRING_PROFILES_ACTIVE=demo
# The demo MySQL is published on ${MYSQL_HOST_PORT:-3307} (host 3306 may be a native MySQL).
export MYSQL_JDBC_URL="jdbc:mysql://127.0.0.1:${MYSQL_HOST_PORT:-3307}/o2o_carpooling?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"

SERVICES="gateway-service auth-service user-service driver-service trip-service order-service payment-sim-service map-service file-service ai-service admin-service audit-service notification-service identity-service"
for svc in $SERVICES; do
  jar="backend/$svc/target/$svc-0.5.0-SNAPSHOT.jar"
  if [[ ! -f "$jar" ]]; then echo "MISSING jar: $jar (run ./mvnw package -DskipTests)" >&2; continue; fi
  nohup java -Xmx320m -jar "$jar" > "$LOG_DIR/$svc.log" 2>&1 &
  echo "started $svc (pid $!) -> $LOG_DIR/$svc.log"
  sleep 0.4
done
echo "all launch commands issued; poll http://127.0.0.1:8080..8113/actuator/health"
