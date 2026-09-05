#!/usr/bin/env bash
# 业务项目的构建：只依赖框架 jar，不碰框架源码。
set -euo pipefail
cd "$(dirname "$0")"

JAR=../dist/tinyweb.jar
[ -f "$JAR" ] || { echo "先在上级目录跑 ./build.sh 生成 $JAR"; exit 1; }

rm -rf out && mkdir -p out
javac --release 21 -cp "$JAR" -d out $(find src -name '*.java')
echo "built -> demo/out/"
