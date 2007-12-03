@echo off

rem $Id$

if not "%JAVA_HOME%" == "" goto gotJavaHome

echo Java environment not found. Please set
echo your JAVA_HOME environment variable to
echo the home of your JDK.
goto :eof

:gotJavaHome
if not "%EXIST_HOME%" == "" goto gotExistHome

rem try to guess
set EXIST_HOME=.
if exist %EXIST_HOME%\start.jar goto gotExistHome
set EXIST_HOME=..
if exist %EXIST_HOME%\start.jar goto gotExistHome

echo EXIST_HOME not found. Please set your
echo EXIST_HOME environment variable to the
echo home directory of eXist.
goto :eof

:gotExistHome
"%JAVA_HOME%\bin\java" -Dexist.home="%EXIST_HOME%" -jar "%EXIST_HOME%\start.jar" shutdown %1 %2 %3 %4 %5 %6 %7 %8 %9
:eof