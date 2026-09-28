#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

JAR=../dist/tinyweb.jar
[ -f "$JAR" ] || { echo "先在上级目录跑 ./build.sh 生成 $JAR"; exit 1; }

# MyBatis and H2 JARs
MYBATIS=lib/mybatis.jar
H2=lib/h2.jar
[ -f "$MYBATIS" ] || { echo "缺少 $MYBATIS"; exit 1; }
[ -f "$H2" ] || { echo "缺少 $H2"; exit 1; }

rm -rf out && mkdir -p out
javac --release 21 -cp "$JAR:$MYBATIS:$H2" -d out $(find src -name '*.java')
cp src/mybatis-config.xml out/
echo "built -> out/"
