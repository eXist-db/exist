@echo off

::remove any quotes from JAVA_HOME and EXIST_HOME env var, will be re-added below
for /f "delims=" %%G IN (%JAVA_HOME%) DO SET JAVA_HOME=%%G
for /f "delims=" %%G IN (%EXIST_HOME%) DO SET EXIST_HOME=%%G

rem will be set by the installer
if not "%EXIST_HOME%" == "" goto gotExistHome
set EXIST_HOME=$INSTALL_PATH

:gotExistHome
if not "%JAVA_HOME%" == "" goto gotJavaHome
set JAVA_HOME=$JAVA_HOME

:gotJavaHome
set JAVA_CMD="%JAVA_HOME%\bin\java"

if not "%JAVA_OPTS%" == "" goto gotJavaOpts
set JAVA_OPTS="-Xms32000k -Xmx256000k -Dfile.encoding=UTF-8"

:gotJavaOpts
cd "%EXIST_HOME%"
%JAVA_CMD% "%JAVA_OPTS%" -Dexist.home="%EXIST_HOME%" -jar "%EXIST_HOME%\start.jar" client -U %1 %2 %3 %4 %5 %6 %7 %8 %9

:eof
