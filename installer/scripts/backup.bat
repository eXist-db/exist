@echo off
if not "%JAVA_HOME%" == "" goto gotJavaHome
rem will be set by the installer
set JAVA_HOME="$JAVA_HOME"

:gotJavaHome
if not "%EXIST_HOME%" == "" goto gotExistHome
rem will be set by the installer
set EXIST_HOME=$INSTALL_PATH

:gotExistHome
set JAVA_ENDORSED_DIRS=%EXIST_HOME%\lib\endorsed
set JAVA_OPTS=-Xms16000k -Xmx256000k -Dfile.encoding=UTF-8 -Djava.endorsed.dirs="%JAVA_ENDORSED_DIRS%"

"%JAVA_HOME%\bin\java" %JAVA_OPTS%  -Dexist.home="%EXIST_HOME%" -jar "%EXIST_HOME%\start.jar" backup %1 %2 %3 %4 %5 %6 %7 %8 %9
:eof

