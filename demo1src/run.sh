#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
./build.sh
exec java -Dtinyweb.home=. -cp out:../dist/tinyweb.jar demo.Main "${1:-8080}"
