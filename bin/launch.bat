@echo off

rem $Id: startup.bat 10899 2009-12-28 18:07:14Z dizzzz $
rem
rem In addition to the other parameter options for the Jetty container 
rem pass -j or --jmx to enable JMX agent.  The port for it can be specified 
rem with optional port number e.g. -j1099 or --jmx=1099.
rem

set JMX_ENABLED=0
set JMX_PORT=1099
set JAVA_ARGS=

if not "%JAVA_HOME%" == "" goto gotJavaHome

rem @WINDOWS_INSTALLER_1@

if not "%JAVA_HOME%" == "" goto gotJavaHome

echo Java environment not found. Please set
echo your JAVA_HOME environment variable to
echo the home of your JDK.
goto :eof

:gotJavaHome
rem @WINDOWS_INSTALLER_2@

if not "%EXIST_HOME%" == "" goto gotExistHome

rem try to guess
set EXIST_HOME=.

if exist "%EXIST_HOME%\start.jar" goto gotExistHome
set EXIST_HOME=..
if exist "%EXIST_HOME%\start.jar" goto gotExistHome

echo EXIST_HOME not found. Please set your
echo EXIST_HOME environment variable to the
echo home directory of eXist.
goto :eof

:gotExistHome
set JAVA_ENDORSED_DIRS="%EXIST_HOME%"\lib\endorsed
set JAVA_OPTS="-Xms128m -Xmx512m -Dfile.encoding=UTF-8 -Djava.endorsed.dirs=%JAVA_ENDORSED_DIRS%"

set BATCH.D="%EXIST_HOME%\bin\batch.d"
call %BATCH.D%\get_opts.bat %*
call %BATCH.D%\check_jmx_status.bat

"%JAVA_HOME%\bin\java" "%JAVA_OPTS%"  -Dexist.home="%EXIST_HOME%" -jar "%EXIST_HOME%\start.jar" launch %JAVA_ARGS%
:eof

