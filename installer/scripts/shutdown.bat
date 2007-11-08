@echo off
if not "%JAVA_HOME%" == "" goto gotJavaHome
rem will be set by the installer
set JAVA_HOME=$JAVA_HOME

:gotJavaHome
if not "%EXIST_HOME%" == "" goto gotExistHome
rem will be set by the installer
set EXIST_HOME=$INSTALL_PATH

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