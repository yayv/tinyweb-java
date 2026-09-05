#!/usr/bin/env bash
# 只编译框架，打成 dist/tinyweb.jar。零依赖，只需 JDK 21+。
# demo/ 不在编译范围内——它是"业务项目"，编译时把本 jar 放进 classpath。
set -euo pipefail
cd "$(dirname "$0")"

VERSION="${TINYWEB_VERSION:-0.2.0}"
rm -rf out/classes
mkdir -p out/classes dist

javac --release 21 -Xlint:all -d out/classes \
    $(find src/main/java/top/x0a/tinyweb -name '*.java')

jar --create --file dist/tinyweb.jar \
    --main-class top.x0a.tinyweb.Bootstrap \
    -C out/classes .

echo "built -> dist/tinyweb.jar (v$VERSION)"
echo
echo "业务项目用法："
echo "  javac -cp dist/tinyweb.jar -d <out> <你的源码>"
echo "  java  -Dtinyweb.home=<你的目录> -cp <out>:dist/tinyweb.jar top.x0a.tinyweb.Bootstrap [port]"
