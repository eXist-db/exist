@echo off
if not "%JAVA_HOME%" == "" goto gotJavaHome
echo Java environment not found. Please set
echo your JAVA_HOME environment variable to
echo the home of you JDK.

goto :eof

:gotJavaHome
if not "%EXIST_HOME%" == "" goto gotExistHome
set EXIST_HOME=..

:gotExistHome
set _LIBJARS=%EXIST_HOME%\start.jar;%EXIST_HOME%\exist.jar;%EXIST_HOME%\examples.jar
set JAVA_ENDORSED_DIRS="%EXIST_HOME%"\lib\endorsed
set JAVA_OPTS=-Xms32000k -Xmx256000k -Dfile.encoding=UTF-8 -Djava.endorsed.dirs="%JAVA_ENDORSED_DIRS%"

%JAVA_HOME%\bin\java -Xms32000k -Xmx64000k -classpath %_LIBJARS% %1 %2 %3 %4 %5 %6 %7 %8

:eof
