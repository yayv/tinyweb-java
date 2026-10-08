#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
./build.sh
# 框架 jar 的查找顺序与 build.gradle 一致：先 lib/，再 tinyweb-java 仓库里的 ../dist/
TINYWEB_JAR=lib/tinyweb.jar
[ -f "$TINYWEB_JAR" ] || TINYWEB_JAR=../dist/tinyweb.jar
exec java -Dtinyweb.home=. -cp build/classes/java/main:"$TINYWEB_JAR" demo.Main "${1:-8080}"
