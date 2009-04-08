@echo off

rem will be set by the installer
set EXIST_HOME=$INSTALL_PATH
rem will be set by the installer
set JAVA_HOME=$JDKPath

:gotJavaHome
set JAVA_CMD="%JAVA_HOME%\bin\java"

set JAVA_ENDORSED_DIRS="%EXIST_HOME%"\lib\endorsed
set JAVA_OPTS="-Xms16000k -Xmx128000k -Djava.endorsed.dirs=%JAVA_ENDORSED_DIRS%"

echo "JAVA_HOME: %JAVA_HOME%"
echo "EXIST_HOME: %EXIST_HOME%"

%JAVA_CMD% "%JAVA_OPTS%" -Dexist.home="%EXIST_HOME%" -jar "%EXIST_HOME%\start.jar" org.exist.Setup %1 %2 %3 %4 %5 %6 %7 %8 %9

:eof
