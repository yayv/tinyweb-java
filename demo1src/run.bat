@echo off
REM 业务项目的启动：先构建，再用 classpath 挂框架 jar 跑 demo.Main。对应 run.sh。
REM 端口默认 8080，可用第一个参数覆盖。
setlocal enabledelayedexpansion

REM 切到脚本所在目录（对应 run.sh 的 cd "$(dirname "$0")"）
cd /d "%~dp0"

REM 先构建（.bat 调 .bat 必须用 call，否则不返回，父脚本会被顶掉）
call "%~dp0build.bat"
if errorlevel 1 (
    echo [错误] 构建失败，已中止启动。
    exit /b 1
)

REM 找框架 jar：先 lib\tinyweb.jar，再 ..\dist\tinyweb.jar（与 build.gradle / run.sh 一致）
set "TINYWEB_JAR=lib\tinyweb.jar"
if not exist "%TINYWEB_JAR%" set "TINYWEB_JAR=..\dist\tinyweb.jar"
if not exist "%TINYWEB_JAR%" (
    echo [错误] 找不到 tinyweb.jar：放到 lib\tinyweb.jar，或先在 tinyweb-java 下跑 build.bat 生成 ..\dist\tinyweb.jar。
    exit /b 1
)

REM 端口：默认 8080，可用第一个参数覆盖（对应 run.sh 的 ${1:-8080}）
set "PORT=%~1"
if not defined PORT set "PORT=8080"

REM 启动：classpath 用分号 ;（Windows）；tinyweb.home 指向当前目录，运行时从这里读 configs/ 与 resource/
java -Dtinyweb.home=. -cp "build\classes\java\main;%TINYWEB_JAR%" demo.Main %PORT%

endlocal
