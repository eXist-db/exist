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
set JAVA_ENDORSED_DIRS="%EXIST_HOME%"\lib\endorsed
set JAVA_OPTS="-Xms32000k -Xmx256000k -Dfile.encoding=UTF-8 -Djava.endorsed.dirs=%JAVA_ENDORSED_DIRS%"

:gotJavaOpts
cd "%EXIST_HOME%"
%JAVA_CMD% "%JAVA_OPTS%" -Dexist.home="%EXIST_HOME%" -jar "%EXIST_HOME%\start.jar" client -U %1 %2 %3 %4 %5 %6 %7 %8 %9

:eof
