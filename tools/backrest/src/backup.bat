@echo off

rem $Id: backup.bat 10899 2009-12-28 18:07:14Z dizzzz $

rem Slurp the command line arguments. This loop allows for an unlimited number
rem of arguments (up to the command line limit, anyway).

set CMD_LINE_ARGS=%1
if ""%1""=="""" goto doneStart
shift
:setupArgs
if ""%1""=="""" goto doneStart
set CMD_LINE_ARGS=%CMD_LINE_ARGS% %1
shift
goto setupArgs

rem This label provides a place for the argument list loop to break out
rem and for NT handling to skip to.

:doneStart

if not "%JAVA_HOME%" == "" goto gotJavaHome

rem @WINDOWS_INSTALLER_1@

echo Java environment not found. Please set
echo your JAVA_HOME environment variable to
echo the home of your JDK.
goto :eof

:gotJavaHome
if not "%EXIST_BACKREST_HOME%" == "" goto gotExistHome

rem try to guess (will be overridden by the installer)
set EXIST_BACKREST_HOME=.

rem @WINDOWS_INSTALLER_2@

if exist "%EXIST_BACKREST_HOME%\lib\exist-backrest.jar" goto gotExistHome

set EXIST_BACKREST_HOME=..
if exist "%EXIST_BACKREST_HOME%\lib\exist-backrest.jar" goto gotExistHome

echo EXIST_BACKREST_HOME not found. Please set your
echo EXIST_BACKREST_HOME environment variable to the
echo home directory of eXist BackRest.
goto :eof

:gotExistHome
set JAVA_OPTS=-Xms128m -Xmx512m -Dfile.encoding=UTF-8 

"%JAVA_HOME%\bin\java" %JAVA_OPTS% -Dexist.home="%EXIST_BACKREST_HOME%" -cp "%EXIST_BACKREST_HOME%\lib\exist-backrest.jar" org.exist.backup.Main %CMD_LINE_ARGS%
:eof

