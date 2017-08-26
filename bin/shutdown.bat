@echo off

::remove any quotes from JAVA_HOME and EXIST_HOME env vars if present
for /f "delims=" %%G IN ("%JAVA_HOME%") DO SET "JAVA_HOME=%%~G"
for /f "delims=" %%G IN ("%EXIST_HOME%") DO SET "EXIST_HOME=%%~G"

:: copy the command line args preserving equals chars etc. for things like -ouri=http://something
for /f "tokens=" %%x IN ("%*") DO SET "CMD_LINE_ARGS=%%x"

rem This label provides a place for the argument list loop to break out
rem and for NT handling to skip to.

:doneStart

set JAVA_RUN="java"

if not "%JAVA_HOME%" == "" (
    set JAVA_RUN="%JAVA_HOME%\bin\java"
    goto gotJavaHome
)

rem @WINDOWS_INSTALLER_1@

echo WARNING: JAVA_HOME not found in your environment.
echo.
echo Please, set the JAVA_HOME variable in your enviroment to match the
echo location of the Java Virtual Machine you want to use in case of run fail.
echo.

:gotJavaHome
rem @WINDOWS_INSTALLER_2@

if not "%EXIST_HOME%" == "" goto gotExistHome

rem try to guess (will be set by the installer)
set EXIST_HOME=.

if exist "%EXIST_HOME%\start.jar" goto gotExistHome
set EXIST_HOME=..
if exist "%EXIST_HOME%\start.jar" goto gotExistHome

echo EXIST_HOME not found. Please set your
echo EXIST_HOME environment variable to the
echo home directory of eXist.
goto :eof

:gotExistHome
%JAVA_RUN% -Dexist.home="%EXIST_HOME%" -jar "%EXIST_HOME%\start.jar" shutdown %CMD_LINE_ARGS%
:eof
