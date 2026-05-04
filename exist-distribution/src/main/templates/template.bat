@REM
@REM eXist-db Open Source Native XML Database
@REM Copyright (C) 2001 The eXist-db Authors
@REM
@REM info@exist-db.org
@REM http://www.exist-db.org
@REM
@REM This library is free software; you can redistribute it and/or
@REM modify it under the terms of the GNU Lesser General Public
@REM License as published by the Free Software Foundation; either
@REM version 2.1 of the License, or (at your option) any later version.
@REM
@REM This library is distributed in the hope that it will be useful,
@REM but WITHOUT ANY WARRANTY; without even the implied warranty of
@REM MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
@REM Lesser General Public License for more details.
@REM
@REM You should have received a copy of the GNU Lesser General Public
@REM License along with this library; if not, write to the Free Software
@REM Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
@REM

@echo off

set ERROR_CODE=0

:init
@REM Decide how to startup depending on the version of windows

@REM -- Win98ME
if NOT "%OS%"=="Windows_NT" goto Win9xArg

@REM set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" @setlocal

@REM -- 4NT shell
if "%eval[2+2]" == "4" goto 4NTArgs

@REM -- Regular WinNT shell
set CMD_LINE_ARGS=%*
goto WinNTGetScriptDir

@REM The 4NT Shell from jp software
:4NTArgs
set CMD_LINE_ARGS=%$
goto WinNTGetScriptDir

:Win9xArg
@REM Slurp the command line arguments.  This loop allows for an unlimited number
@REM of arguments (up to the command line limit, anyway).
set CMD_LINE_ARGS=
:Win9xApp
if %1a==a goto Win9xGetScriptDir
set CMD_LINE_ARGS=%CMD_LINE_ARGS% %1
shift
goto Win9xApp

:Win9xGetScriptDir
set SAVEDIR=%CD%
%0\
cd %0\..\.. 
set BASEDIR=%CD%
cd %SAVEDIR%
set SAVE_DIR=
goto repoSetup

:WinNTGetScriptDir
for %%i in ("%~dp0..") do set "BASEDIR=%%~fi"

@REM Get the script name and determine the command to run
set SCRIPT_NAME=%~n0
set EXIST_COMMAND=

@REM Check script name and set appropriate command
if /i "%SCRIPT_NAME%"=="client" set EXIST_COMMAND=client
if /i "%SCRIPT_NAME%"=="backup" set EXIST_COMMAND=backup
if /i "%SCRIPT_NAME%"=="export-gui" set EXIST_COMMAND=export-gui
if /i "%SCRIPT_NAME%"=="jmxclient" set EXIST_COMMAND=jmxclient
if /i "%SCRIPT_NAME%"=="launcher" set EXIST_COMMAND=launcher
if /i "%SCRIPT_NAME%"=="shutdown" set EXIST_COMMAND=shutdown
if /i "%SCRIPT_NAME%"=="startup" set EXIST_COMMAND=jetty
if /i "%SCRIPT_NAME%"=="export" set EXIST_COMMAND=export --export --zip

@REM Default command if no match
if "%EXIST_COMMAND%"=="" set EXIST_COMMAND=launch

:repoSetup
set REPO=


if "%JAVACMD%"=="" set JAVACMD=java

if "%REPO%"=="" set REPO=%BASEDIR%\lib

set CLASSPATH="%BASEDIR%"\etc;"%REPO%"\*

if NOT "%CLASSPATH_PREFIX%" == "" set CLASSPATH=%CLASSPATH_PREFIX%;%CLASSPATH%

@REM Reaching here means variables are defined and arguments have been captured
:endInit

@REM Normalize classpath and property path quoting for Windows
set "CLASSPATH=%BASEDIR%\etc;%REPO%\*"
if NOT "%CLASSPATH_PREFIX%" == "" set "CLASSPATH=%CLASSPATH_PREFIX%;%CLASSPATH%"

%JAVACMD% %JAVA_OPTS% -Xms128m -XX:+UseNUMA -XX:+UseZGC -XX:+UseStringDeduplication ^
    -Dexist.autodeploy.dir="$BASEDIR\autodeploy" ^
    -Dexist.configurationFile="%BASEDIR%\etc\conf.xml" ^
    -Dexist.home="%BASEDIR%" ^
    -Dexist.jetty.config="%BASEDIR%\etc\jetty\standard.enabled-jetty-configs" ^
    -Dfile.encoding=UTF-8 ^
    -Djetty.home="%BASEDIR%" ^
    -Dlog4j.configurationFile="%BASEDIR%\etc\log4j2.xml" ^
    -classpath "%CLASSPATH%" org.exist.start.Main %EXIST_COMMAND% %CMD_LINE_ARGS%
if %ERRORLEVEL% NEQ 0 goto error
goto end

:error
if "%OS%"=="Windows_NT" @endlocal
set ERROR_CODE=%ERRORLEVEL%

:end
@REM set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" goto endNT

@REM For old DOS remove the set variables from ENV - we assume they were not set
@REM before we started - at least we don't leave any baggage around
set CMD_LINE_ARGS=
goto postExec

:endNT
@REM If error code is set to 1 then the endlocal was done already in :error.
if %ERROR_CODE% EQU 0 @endlocal


:postExec

if "%FORCE_EXIT_ON_ERROR%" == "on" (
  if %ERROR_CODE% NEQ 0 exit %ERROR_CODE%
)

exit /B %ERROR_CODE%
