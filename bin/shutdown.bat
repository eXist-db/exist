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


if not "%JAVA_HOME%" == "" goto gotJavaHome

echo Java environment not found. Please set
echo your JAVA_HOME environment variable to
echo the home of your JDK.
goto :eof

:gotJavaHome
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
"%JAVA_HOME%\bin\java" -Dexist.home="%EXIST_HOME%" -jar "%EXIST_HOME%\start.jar" shutdown %CMD_LINE_ARGS%
:eof