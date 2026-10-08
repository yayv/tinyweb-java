#!/usr/bin/env bash
# 业务项目的构建：只依赖框架 jar，不碰框架源码。
set -euo pipefail
cd "$(dirname "$0")"

gradle --quiet classes
echo "built -> build/classes/java/main/"
