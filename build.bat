@echo off
REM 只编译框架，打成 dist\tinyweb.jar。零依赖，只需 JDK 21+（无需 Gradle）。
REM demo\ 不在编译范围内——它是"业务项目"，编译时把本 jar 放进 classpath。
setlocal enabledelayedexpansion

REM 切到脚本所在目录（对应 build.sh 的 cd "$(dirname "$0")"）
cd /d "%~dp0"

if not defined TINYWEB_VERSION set "TINYWEB_VERSION=0.2.0"

REM 检查 JDK 是否就绪
where javac >nul 2>nul
if errorlevel 1 (
    echo [错误] 找不到 javac，请先安装 JDK 21+ 并把 bin 目录加入 PATH。
    exit /b 1
)

REM 清理并重建输出目录
if exist out\classes rd /s /q out\classes
if not exist out\classes mkdir out\classes
if not exist dist mkdir dist

REM 收集所有框架源码到参数文件（javac @file），替代 Unix 的 find
set "SOURCES=out\sources.txt"
dir /s /b "src\main\java\top\x0a\tinyweb\*.java" > "%SOURCES%"

REM 编译
javac --release 21 -Xlint:all -d out\classes "@%SOURCES%"
if errorlevel 1 (
    echo [错误] 编译失败。
    del "%SOURCES%" 2>nul
    exit /b 1
)
del "%SOURCES%" 2>nul

REM 打包：入口 Tools，连同 resource 一并打入
jar --create --file dist\tinyweb.jar ^
    --main-class top.x0a.tinyweb.Tools ^
    -C out\classes . ^
    -C resource .
if errorlevel 1 (
    echo [错误] 打包失败。
    exit /b 1
)

echo built -^> dist\tinyweb.jar ^(v%TINYWEB_VERSION%^)
echo.
echo 业务项目用法：
echo   javac -cp dist\tinyweb.jar -d ^<out^> ^<你的源码^>
echo   多站点：cd ^<部署目录^> ^&^& java -jar tinyweb.jar [port^|list^|check ^<name^>^|gen-default]

endlocal
