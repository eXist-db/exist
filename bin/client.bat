@echo off
if not "%JAVA_HOME%" == "" goto gotJavaHome
echo Java environment not found. Please set
echo your JAVA_HOME environment variable to
echo the home of your JDK.
goto :eof

:gotJavaHome
if not "%EXIST_HOME%" == "" goto gotExistHome
echo EXIST_HOME not found. Please set your
echo EXIST_HOME environment variable to the
echo home directory of eXist.
goto :eof

:gotExistHome
if not "%EXIST_BASE%" == "" goto gotExistBase
set EXIST_BASE=%EXIST_HOME%

:gotExistBase
set _LIBJARS=%EXIST_BASE%;%EXIST_BASE%\exist.jar
for %%i in (%EXIST_BASE%\lib\core\*.jar) do call %EXIST_BASE%\bin\cpappend.bat %%i

if not "%JAVA_OPTS%" == "" goto gotJavaOpts
set JAVA_OPTS=-Xms32000k -Xmx128000k

:gotJavaOpts
%JAVA_HOME%\bin\java %JAVA_OPTS% -Dexist.home=%EXIST_HOME% -classpath %_LIBJARS% org.exist.InteractiveClient %1 %2 %3 %4 %5 %6 %7 %8
:eof
