@echo off

rem $Id$

rem will be set by the installer
set EXIST_HOME=$INSTALL_PATH
rem will be set by the installer
set JAVA_HOME=$JAVA_HOME

:gotJavaHome
set JAVA_CMD="%JAVA_HOME%\bin\java"

set JAVA_ENDORSED_DIRS="%EXIST_HOME%"\lib\endorsed
set JAVA_OPTS="-Xms64m -Xmx768m -Djava.endorsed.dirs=%JAVA_ENDORSED_DIRS%"

rem echo "JAVA_HOME: %JAVA_HOME%"
rem echo "EXIST_HOME: %EXIST_HOME%"
echo %JAVA_OPTS%

%JAVA_CMD% "%JAVA_OPTS%" -Dexist.home="%EXIST_HOME%" -Duse.autodeploy.feature=false -jar "%EXIST_HOME%\start.jar" org.exist.installer.Setup %1 %2 %3 %4 %5 %6 %7 %8 %9

:eof