@echo off

if not "%JAVA_HOME%" == "" goto gotJavaHome
echo Java environment not found. Please set
echo your JAVA_HOME environment variable to
echo the home of you JDK.
goto :eof

:gotJavaHome
set _LIBJARS=%CLASSPATH%;exist.jar;%JAVA_HOME%\lib\tools.jar;lib\core\ant.jar;lib\optional\jakarta-regexp-1.2.jar

echo eXist Build
echo -------------------

echo Building with classpath %_LIBJARS%

echo Starting Ant...

java -classpath %_LIBJARS% org.apache.tools.ant.Main %1 %2 %3 %4 %5
