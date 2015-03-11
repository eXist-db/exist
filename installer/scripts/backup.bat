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

rem will be set by the installer
set JAVA_HOME="$JDKPath"

rem second check
if not "%JAVA_HOME%" == "" goto gotJavaHome


echo WARNING: JAVA_HOME not found in your environment.
echo.
echo Please, set the JAVA_HOME variable in your enviroment to match the
echo location of the Java Virtual Machine you want to use in case of run fail.
echo.

:gotJavaHome
if not "%EXIST_HOME%" == "" goto gotExistHome

rem try to guess (will be overridden by the installer)
set EXIST_HOME=.

rem will be set by the installer
set EXIST_HOME=$INSTALL_PATH


if exist "%EXIST_HOME%\start.jar" goto gotExistHome

set EXIST_HOME=..
if exist "%EXIST_HOME%\start.jar" goto gotExistHome

echo EXIST_HOME not found. Please set your
echo EXIST_HOME environment variable to the
echo home directory of eXist.
goto :eof

:gotExistHome
set MX=768
rem will be set by the installer
set MX=$MAX_MEMORY


set JAVA_ENDORSED_DIRS="%EXIST_HOME%\lib\endorsed"
set JAVA_OPTS="-Xms128m -Xmx%MX%m -Dfile.encoding=UTF-8 -Djava.endorsed.dirs=%JAVA_ENDORSED_DIRS%"

%JAVA_RUN% "%JAVA_OPTS%"  -Dexist.home="%EXIST_HOME%" -jar "%EXIST_HOME%\start.jar" backup %CMD_LINE_ARGS%
:eof

