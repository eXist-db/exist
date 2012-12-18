@echo off
setlocal

rem
rem Copyright (c) 1999, 2012 Tanuki Software, Ltd.
rem http://www.tanukisoftware.com
rem All rights reserved.
rem
rem This software is the proprietary information of Tanuki Software.
rem You shall use it only in accordance with the terms of the
rem license agreement you entered into with Tanuki Software.
rem http://wrapper.tanukisoftware.com/doc/english/licenseOverview.html
rem
rem Java Service Wrapper general startup script.
rem

rem -----------------------------------------------------------------------------
rem These settings can be modified to fit the needs of your application
rem Optimized for use with version 3.5.17 of the Wrapper.

rem The base name for the Wrapper binary.
set _WRAPPER_BASE=wrapper

rem The name and location of the Wrapper configuration file.   This will be used
rem  if the user does not specify a configuration file as the first parameter to
rem  this script.  It will not be possible to specify a configuration file on the
rem  command line if _PASS_THROUGH is set.
set _WRAPPER_CONF_DEFAULT=../conf/wrapper.conf

rem Makes it possible to override the Wrapper configuration file by specifying it
rem  as the first parameter.
rem set _WRAPPER_CONF_OVERRIDE=true

rem _PASS_THROUGH tells the script to pass all parameters through to the JVM as
rem  is.
rem set _PASS_THROUGH=true

rem Do not modify anything beyond this point
rem -----------------------------------------------------------------------------

rem
rem Resolve the real path of the wrapper.exe
rem  For non NT systems, the _REALPATH and _WRAPPER_CONF values
rem  can be hard-coded below and the following test removed.
rem
if "%OS%"=="Windows_NT" goto nt
echo This script only works with NT-based versions of Windows.
goto :eof

:nt
rem
rem Find the application home.
rem
rem %~dp0 is location of current script under NT
set _REALPATH=%~dp0

rem
rem Decide on the specific Wrapper binary to use (See delta-pack)
rem
if "%PROCESSOR_ARCHITEW6432%"=="AMD64" goto amd64
if "%PROCESSOR_ARCHITECTURE%"=="AMD64" goto amd64
if "%PROCESSOR_ARCHITECTURE%"=="IA64" goto ia64
set _WRAPPER_L_EXE=%_REALPATH%%_WRAPPER_BASE%-windows-x86-32.exe
goto search
:amd64
set _WRAPPER_L_EXE=%_REALPATH%%_WRAPPER_BASE%-windows-x86-64.exe
goto search
:ia64
set _WRAPPER_L_EXE=%_REALPATH%%_WRAPPER_BASE%-windows-ia-64.exe
goto search
:search
set _WRAPPER_EXE=%_WRAPPER_L_EXE%
if exist "%_WRAPPER_EXE%" goto conf
set _WRAPPER_EXE=%_REALPATH%%_WRAPPER_BASE%.exe
if exist "%_WRAPPER_EXE%" goto conf
echo Unable to locate a Wrapper executable using any of the following names:
echo %_WRAPPER_L_EXE%
echo %_WRAPPER_EXE%
pause
goto :eof

rem
rem Find the wrapper.conf
rem
:conf
if not [%_WRAPPER_CONF_OVERRIDE%]==[] (
    set _WRAPPER_CONF="%~f1"
    if not [%_WRAPPER_CONF%]==[""] (
        shift
        goto :startup
    )
)
set _WRAPPER_CONF="%_WRAPPER_CONF_DEFAULT%"

rem
rem Start the Wrapper
rem
:startup

rem Collect an parameters
:parameters
set _PARAMETERS=%_PARAMETERS% %1
shift
if not [%1]==[] goto :parameters

if [%_PASS_THROUGH%]==[] (
    "%_WRAPPER_EXE%" -i %_WRAPPER_CONF%
) else (
    "%_WRAPPER_EXE%" -i %_WRAPPER_CONF% -- %_PARAMETERS%
)
if not errorlevel 1 goto :eof
pause

