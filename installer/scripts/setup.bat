@echo off


rem $Id$

::remove any quotes from JAVA_HOME and EXIST_HOME env var, will be re-added below
for /f "delims=" %%G IN (%JAVA_HOME%) DO SET JAVA_HOME=%%G
for /f "delims=" %%G IN (%EXIST_HOME%) DO SET EXIST_HOME=%%G

rem will be set by the installer
set EXIST_HOME=$INSTALL_PATH
rem will be set by the installer
set JAVA_HOME=$JAVA_HOME

:gotJavaHome
set JAVA_CMD="%JAVA_HOME%\bin\java"

set JAVA_OPTS="-Xms64m -Xmx768m"

rem make sure there's the jetty tmp directory
mkdir "%EXIST_HOME%\tools\jetty\tmp"

rem echo "JAVA_HOME: %JAVA_HOME%"
rem echo "EXIST_HOME: %EXIST_HOME%"
echo %JAVA_OPTS%

%JAVA_CMD% "%JAVA_OPTS%" -Dexist.home="%EXIST_HOME%" -Duse.autodeploy.feature=false -jar "%EXIST_HOME%\start.jar" org.exist.installer.Setup %1 %2 %3 %4 %5 %6 %7 %8 %9

:eof
