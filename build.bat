@echo off

if not "%JAVA_HOME%" == "" goto gotJavaHome
echo Java environment not found. Please set
echo your JAVA_HOME environment variable to
echo the home of you JDK.
goto :eof

:gotJavaHome
set _LIBJARS=%CLASSPATH%;"%EXIST_HOME%"\lib\core\ant-launcher.jar;%EXIST_HOME%\lib\core\junit.jar:%EXIST_HOME%\lib\core\jakarta-oro-2.0.6.jar;%JAVA_HOME%\lib\tools.jar

set JAVA_ENDORSED_DIRS="%EXIST_HOME%"\lib\endorsed
set JAVA_OPTS=-Xms32000k -Xmx256000k -Djava.endorsed.dirs="%JAVA_ENDORSED_DIRS%"

echo eXist Build
echo -------------------

echo Building with classpath %_LIBJARS%

echo Starting Ant...

java %JAVA_OPTS% -classpath %_LIBJARS% org.apache.tools.ant.Main %1 %2 %3 %4 %5
