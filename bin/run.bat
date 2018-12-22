@echo off

rem $Id$

rem Slurp the command line arguments. This loop allows for an unlimited number
rem of arguments (up to the command line limit, anyway).

set CMD_LINE_ARGS=%1
if ""%1""=="""" goto doneStart
shift
:setupArgs
if ""%1""=="""" goto doneStart
set CMD_LINE_ARGS=%CMD_LINE_ARGS% %1
shift
goto setupArgs

rem This label provides a place for the argument list loop to break out
rem and for NT handling to skip to.

:doneStart
set JAVA_RUN="java"

if not "%JAVA_HOME%" == "" (
    set JAVA_RUN="%JAVA_HOME%\bin\java"
    goto gotJavaHome
)

echo WARNING: JAVA_HOME not found in your environment.
echo.
echo Please, set the JAVA_HOME variable in your enviroment to match the
echo location of the Java Virtual Machine you want to use in case of run fail.
echo.

:gotJavaHome

if not "%EXIST_APP_HOME%" == "" goto gotExistAppHome
set EXIST_APP_HOME="%~dp0\.."
if exist "%EXIST_APP_HOME%\start.jar" goto gotExistAppHome
echo EXIST_APP_HOME not found. Please set your
echo EXIST_APP_HOME environment variable to the
echo installation directory of eXist-db.
goto :eof
:gotExistAppHome

if not "%JAVA_OPTIONS%" == "" goto doneJavaOptions
set MX=2048
set JAVA_OPTIONS="-Xms128m -Xmx%MX%m -Dfile.encoding=UTF-8"
:doneJavaOptions

if "%EXIST_HOME%" == "" goto doneExistHome
set OPTIONS="-Dexist.home=%EXIST_HOME%"
:doneExistHome

:gotJavaOpts
%JAVA_RUN% "%JAVA_OPTIONS%" "%OPTIONS%" -jar "%EXIST_APP_HOME%\start.jar" %CMD_LINE_ARGS%
:eof
