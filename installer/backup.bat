@echo off

rem will be set by the installer
if not "%EXIST_HOME%" == "" goto gotExistHome
set EXIST_HOME=$INSTALL_PATH

:gotExistHome
if not "%JAVA_HOME%" == "" goto gotJavaHome
set JAVA_HOME=$JAVA_HOME

:gotJavaHome
set JAVA_CMD="%JAVA_HOME%\bin\java"

if not "%JAVA_OPTS%" == "" goto gotJavaOpts
set JAVA_OPTS=-Xms32000k -Xmx256000k

:gotJavaOpts
%JAVA_CMD% "%JAVA_OPTS%" -Dexist.home="%EXIST_HOME%" -jar "%EXIST_HOME%\start.jar" backup %1 %2 %3 %4 %5 %6 %7

:eof
