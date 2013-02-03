@echo off

rem $Id$
rem
rem In addition to the other parameter options for the standalone server 
rem pass -j or --jmx to enable JMX agent.  The port for it can be specified 
rem with optional port number e.g. -j1099 or --jmx=1099.
rem

set JMX_ENABLED=0
set JMX_PORT=1099
set JAVA_ARGS=

set JAVA_RUN="java"

if not "%JAVA_HOME%" == "" (
    set JAVA_RUN="%JAVA_HOME%\bin\java"
    goto gotJavaHome
)

rem @WINDOWS_INSTALLER_1@

echo WARNING: JAVA_HOME not found in your environment.
echo.
echo Please, set the JAVA_HOME variable in your enviroment to match the
echo location of the Java Virtual Machine you want to use in case of run fail.
echo.

rem :gotJavaHome
rem @WINDOWS_INSTALLER_2@

if not "%EXIST_HOME%" == "" goto gotExistHome

rem try to guess (will be set by the installer)
set EXIST_HOME=.

if exist "%EXIST_HOME%\start.jar" goto gotExistHome
set EXIST_HOME=..
if exist "%EXIST_HOME%\start.jar" goto gotExistHome

echo EXIST_HOME not found. Please set your
echo EXIST_HOME environment variable to the
echo home directory of eXist.
goto :eof

:gotExistHome
set MX=1024
rem @WINDOWS_INSTALLER_3@

set JAVA_ENDORSED_DIRS="%EXIST_HOME%"\lib\endorsed
set JAVA_OPTS="-Xms128m -Xmx%MX%m -Dfile.encoding=UTF-8 -Djava.endorsed.dirs=%JAVA_ENDORSED_DIRS%"

set BATCH.D="%EXIST_HOME%\bin\batch.d"
call %BATCH.D%\get_opts.bat %*
call %BATCH.D%\check_jmx_status.bat

%JAVA_RUN% "%JAVA_OPTS%" -Dexist.home="%EXIST_HOME%" -jar "%EXIST_HOME%\start.jar" standalone %JAVA_ARGS%
:eof
