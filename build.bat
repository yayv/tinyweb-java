@echo off
REM 只编译框架，打成 dist\tinyweb.jar。零依赖，只需 JDK 21+（无需 Gradle）。
REM demo\ 不在编译范围内——它是"业务项目"，编译时把本 jar 放进 classpath。
setlocal enabledelayedexpansion

REM 切到脚本所在目录（对应 build.sh 的 cd "$(dirname "$0")"）
cd /d "%~dp0"

if not defined TINYWEB_VERSION set "TINYWEB_VERSION=0.2.0"

REM 定位完整 JDK 的 bin（需要 javac 和 jar 两个工具）。
REM 坑：Oracle 安装器往 PATH 塞的 javapath 目录只有 java/javac，没有 jar.exe，
REM 所以不能只靠 PATH。优先 JAVA_HOME，否则从 java 自报的 java.home 读出来。
set "JDK_BIN="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\jar.exe" set "JDK_BIN=%JAVA_HOME%\bin"

if not defined JDK_BIN (
    for /f "tokens=1,* delims==" %%I in ('java -XshowSettings:properties -version 2^>^&1 ^| findstr /c:"java.home"') do set "JAVA_HOME_DETECTED=%%J"
    REM 去掉 java.home 值的前导空格
    for /f "tokens=* delims= " %%H in ("!JAVA_HOME_DETECTED!") do set "JAVA_HOME_DETECTED=%%H"
    if defined JAVA_HOME_DETECTED if exist "!JAVA_HOME_DETECTED!\bin\jar.exe" set "JDK_BIN=!JAVA_HOME_DETECTED!\bin"
)

if not defined JDK_BIN (
    echo [错误] 找不到完整 JDK 21+（需要 javac 和 jar）。
    echo         请安装 JDK 并把 JAVA_HOME 指向其根目录，或把该 JDK 的 bin 加入 PATH。
    exit /b 1
)

set "JAVAC=%JDK_BIN%\javac"
set "JAR=%JDK_BIN%\jar"

REM 清理并重建输出目录
if exist out\classes rd /s /q out\classes
if not exist out\classes mkdir out\classes
if not exist dist mkdir dist

REM 收集所有框架源码到参数文件（javac @file），替代 Unix 的 find
set "SOURCES=out\sources.txt"
dir /s /b "src\main\java\top\x0a\tinyweb\*.java" > "%SOURCES%"

REM 编译
"%JAVAC%" --release 21 -Xlint:all -d out\classes "@%SOURCES%"
if errorlevel 1 (
    echo [错误] 编译失败。
    del "%SOURCES%" 2>nul
    exit /b 1
)
del "%SOURCES%" 2>nul

REM 打包：入口 Tools，连同 resource 一并打入
"%JAR%" --create --file dist\tinyweb.jar ^
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
