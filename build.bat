@echo off

rem $Id$

if not "%JAVA_HOME%" == "" goto gotJavaHome
echo Java environment not found. Please set
echo your JAVA_HOME environment variable to
echo the home of you JDK.
goto :eof

:gotJavaHome
if not "%EXIST_HOME%" == "" goto gotExistHome
set EXIST_HOME=%CD%

:gotExistHome
set ANT_HOME=%EXIST_HOME%\tools\ant
set _LIBJARS=%CLASSPATH%;%ANT_HOME%\lib\ant-launcher.jar;%EXIST_HOME%\lib\test\junit-4.6.jar;%JAVA_HOME%\lib\tools.jar

set JAVA_ENDORSED_DIRS=%EXIST_HOME%\lib\endorsed
set JAVA_OPTS=-Djava.endorsed.dirs="%JAVA_ENDORSED_DIRS%" -Dant.home="%ANT_HOME%" -Dexist.home="%EXIST_HOME%"

echo eXist Build
echo -------------------
echo JAVA_HOME=%JAVA_HOME%
echo EXIST_HOME=%EXIST_HOME%
echo _LIBJARS=%_LIBJARS%

echo Starting Ant...
echo

"%JAVA_HOME%\bin\java" -Xms512m -Xmx512m %JAVA_OPTS% -classpath "%_LIBJARS%" org.apache.tools.ant.launch.Launcher %1 %2 %3 %4 %5 %6 %7 %8 %9
