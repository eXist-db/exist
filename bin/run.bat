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
set _LIBJARS=%EXIST_HOME%;%EXIST_HOME%\exist.jar
for %%i in (%EXIST_HOME%\lib\*.jar) do call bin\cpappend.bat %%i
%JAVA_HOME%\bin\java -Xms32000k -Xmx64000k -classpath %_LIBJARS% %1 %2 %3 %4 %5 %6 %7 %8

:eof
