@echo off
if not "%JAVA_HOME%" == "" goto gotJavaHome
echo Java environment not found. Please set
echo your JAVA_HOME environment variable to
echo the home of your JDK.
goto :eof

:gotJavaHome
if not "%EXIST_HOME%" == "" goto gotExistHome

rem try to guess
set EXIST_HOME=.
if exist %EXIST_HOME%\exist.jar goto gotExistHome
set EXIST_HOME=..
if exist %EXIST_HOME%\exist.jar goto gotExistHome

echo EXIST_HOME not found. Please set your
echo EXIST_HOME environment variable to the
echo home directory of eXist.
goto :eof

:gotExistHome
if not "%EXIST_BASE%" == "" goto gotExistBase
set EXIST_BASE=%EXIST_HOME%

:gotExistBase
set JETTY_HOME=%EXIST_BASE%\Jetty-4.1.4

echo "EXIST_HOME = %EXIST_HOME%"
echo "EXIST_BASE = %EXIST_BASE%"
echo "JETTY_HOME = %JETTY_HOME%"

set _LIBJARS=%EXIST_BASE%;%EXIST_BASE%\exist.jar;%JAVA_HOME%\lib\tools.jar
for %%i in (%EXIST_BASE%\lib\core\*.jar) do call %EXIST_BASE%\bin\cpappend.bat %%i
for %%i in (%EXIST_BASE%\lib\optional\*.jar) do call %EXIST_BASE%\bin\cpappend.bat %%i
for %%i in (%JETTY_HOME%\lib\*.jar) do call %EXIST_BASE%\bin\cpappend.bat %%i

%JAVA_HOME%\bin\java -Xms32000k -Xmx128000k -Dexist.home="%EXIST_HOME%" -Djetty.home="%JETTY_HOME%" -classpath %_LIBJARS% org.mortbay.jetty.Server "%JETTY_HOME%\etc\jetty.xml"
:eof

