@echo off
if not "%EXIST_HOME%" == "" goto gotExistHome
echo EXIST_HOME not found. Please set your
echo EXIST_HOME environment variable to the
echo home directory of eXist.
goto :eof

:gotExistHome
set _LIBJARS=%EXIST_HOME%;%EXIST_HOME%\exist.jar
for %%i in (%EXIST_HOME%\lib\*.jar) do call %EXIST_HOME%\bin\cpappend.bat %%i

start %JAVA_HOME%\bin\java -Xms32000k -Xmx128000k -classpath %_LIBJARS% org.exist.Server %1 %2 %3 %4
