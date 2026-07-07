#!/usr/bin/env bash
# Launch all backend services with conservative JVM limits for a small VPS.
set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

LOG_DIR="${O2O_LOG_DIR:-/tmp/o2o-logs}"
mkdir -p "$LOG_DIR"

if [[ ! -f .env ]]; then
  echo "error: .env not found - run scripts/generate-local-env.sh first" >&2
  exit 1
fi

while IFS= read -r line; do
  case "$line" in ''|\#*) continue;; esac
  export "$line"
done < .env

export SPRING_PROFILES_ACTIVE=demo
export SERVER_ADDRESS="${O2O_BIND_ADDRESS:-127.0.0.1}"
export MYSQL_JDBC_URL="jdbc:mysql://127.0.0.1:${MYSQL_HOST_PORT:-3307}/o2o_carpooling?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"

JAVA_OPTS="${O2O_JAVA_OPTS:--Xms32m -Xmx128m -XX:MaxMetaspaceSize=96m -XX:ReservedCodeCacheSize=32m -Xss512k -XX:+UseSerialGC -XX:ActiveProcessorCount=1}"
GATEWAY_PORT="${O2O_GATEWAY_PORT:-8120}"
START_DELAY="${O2O_START_DELAY:-4}"
SERVICES="auth-service user-service driver-service trip-service order-service payment-sim-service map-service file-service ai-service admin-service audit-service notification-service identity-service gateway-service"

echo "Using JAVA_OPTS=$JAVA_OPTS"
for svc in $SERVICES; do
  jar="backend/$svc/target/$svc-0.5.0-SNAPSHOT.jar"
  if [[ ! -f "$jar" ]]; then
    echo "MISSING jar: $jar (run ./mvnw package -DskipTests)" >&2
    continue
  fi
  if [[ "$svc" == "gateway-service" ]]; then
    nohup env SERVER_PORT="$GATEWAY_PORT" java $JAVA_OPTS -jar "$jar" > "$LOG_DIR/$svc.log" 2>&1 &
  else
    nohup java $JAVA_OPTS -jar "$jar" > "$LOG_DIR/$svc.log" 2>&1 &
  fi
  echo "started $svc (pid $!) -> $LOG_DIR/$svc.log"
  sleep "$START_DELAY"
done

echo "all launch commands issued; poll gateway http://127.0.0.1:$GATEWAY_PORT/actuator/health and services http://127.0.0.1:8101..8113/actuator/health"
