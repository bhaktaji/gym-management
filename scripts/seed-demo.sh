#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RESET_FLAG="false"

if [[ "${1:-}" == "--reset" || "${DEMO_RESET:-false}" == "true" ]]; then
  RESET_FLAG="true"
fi

export SPRING_PROFILES_ACTIVE="demo"
export MONGODB_URI="${MONGODB_URI:-mongodb://localhost:27017/gymkhata_demo}"
export DEMO_RESET="${RESET_FLAG}"

echo "==> Running GymKhata Demo Seeder (profile=demo, uri=${MONGODB_URI}, reset=${RESET_FLAG})"
cd "${ROOT_DIR}/backend"
mvn test-compile test -Dtest=DemoDataSeederTest -DfailIfNoTests=false -Ddemo.reset="${RESET_FLAG}"
