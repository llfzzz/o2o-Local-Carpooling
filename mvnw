#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MAVEN_VERSION="${MAVEN_VERSION:-3.9.11}"
MAVEN_HOME="${ROOT_DIR}/.mvn-local/apache-maven-${MAVEN_VERSION}"
MAVEN_ARCHIVE="${ROOT_DIR}/.mvn-local/apache-maven-${MAVEN_VERSION}-bin.tar.gz"
MAVEN_URL="https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz"

if [ ! -x "${MAVEN_HOME}/bin/mvn" ]; then
  mkdir -p "${ROOT_DIR}/.mvn-local"
  if [ ! -f "${MAVEN_ARCHIVE}" ]; then
    curl -fsSL "${MAVEN_URL}" -o "${MAVEN_ARCHIVE}"
  fi
  tar -xzf "${MAVEN_ARCHIVE}" -C "${ROOT_DIR}/.mvn-local"
fi

exec "${MAVEN_HOME}/bin/mvn" -f "${ROOT_DIR}/backend/pom.xml" "$@"
