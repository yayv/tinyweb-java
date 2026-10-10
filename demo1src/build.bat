@echo off
REM 业务项目的构建：只依赖框架 jar，不碰框架源码。零依赖，只需 JDK 21+（无需 Gradle）。
REM 编译到 build\classes\java\main（与 run.sh / Gradle 输出路径一致），运行时用 classpath 挂框架 jar。
setlocal enabledelayedexpansion

REM 切到脚本所在目录（对应 build.sh 的 cd "$(dirname "$0")"）
cd /d "%~dp0"

REM 定位完整 JDK 的 bin（这里只需要 javac）。
REM 坑：Oracle 安装器往 PATH 塞的 javapath 目录是存根，别只靠 PATH。
REM 优先 JAVA_HOME，否则从 java 自报的 java.home 读出来。
set "JDK_BIN="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" set "JDK_BIN=%JAVA_HOME%\bin"

if not defined JDK_BIN (
    for /f "tokens=1,* delims==" %%I in ('java -XshowSettings:properties -version 2^>^&1 ^| findstr /c:"java.home"') do set "JAVA_HOME_DETECTED=%%J"
    REM 去掉 java.home 值的前导空格
    for /f "tokens=* delims= " %%H in ("!JAVA_HOME_DETECTED!") do set "JAVA_HOME_DETECTED=%%H"
    if defined JAVA_HOME_DETECTED if exist "!JAVA_HOME_DETECTED!\bin\javac.exe" set "JDK_BIN=!JAVA_HOME_DETECTED!\bin"
)

if not defined JDK_BIN (
    echo [错误] 找不到 JDK 21+ 的 javac。
    echo         请安装 JDK 并把 JAVA_HOME 指向其根目录，或把该 JDK 的 bin 加入 PATH。
    exit /b 1
)

set "JAVAC=%JDK_BIN%\javac"

REM 找框架 jar：先 lib\tinyweb.jar，再 ..\dist\tinyweb.jar（与 build.gradle / run.sh 一致）
set "TINYWEB_JAR=lib\tinyweb.jar"
if not exist "%TINYWEB_JAR%" set "TINYWEB_JAR=..\dist\tinyweb.jar"
if not exist "%TINYWEB_JAR%" (
    echo [错误] 找不到 tinyweb.jar：放到 lib\tinyweb.jar，或先在 tinyweb-java 下跑 build.bat 生成 ..\dist\tinyweb.jar。
    exit /b 1
)

REM 清理并重建输出目录
set "OUT=build\classes\java\main"
if exist "%OUT%" rd /s /q "%OUT%"
mkdir "%OUT%"

REM 收集所有源码到参数文件（javac @file），替代 Unix 的 find
set "SOURCES=build\sources.txt"
dir /s /b "src\*.java" > "%SOURCES%"

REM 编译：挂框架 jar 到 classpath；源码是 UTF-8，务必显式指定，否则 GBK 控制台下中文会乱
"%JAVAC%" --release 21 -encoding UTF-8 -cp "%TINYWEB_JAR%" -d "%OUT%" "@%SOURCES%"
if errorlevel 1 (
    echo [错误] 编译失败。
    del "%SOURCES%" 2>nul
    exit /b 1
)
del "%SOURCES%" 2>nul

echo built -^> %OUT%\
echo.
echo 运行（Windows classpath 用分号 ;）：
echo   java -Dtinyweb.home=. -cp "%OUT%;%TINYWEB_JAR%" demo.Main [port]
echo   例：java -Dtinyweb.home=. -cp "%OUT%;%TINYWEB_JAR%" demo.Main 8080

endlocal
