@echo off
rem ---------------------------------------------------------------------------
rem shutdown.bat - Stop Script for the CATALINA Server
rem
rem $Id: shutdown.bat,v 1.3 2002/06/24 13:02:39 wolfgang_m Exp $
rem ---------------------------------------------------------------------------

if not "%JAVA_HOME%" == "" goto gotJavaHome
echo Java environment not found. Please set
echo your JAVA_HOME environment variable to
echo the home of you JDK.
goto :finish

:gotJavaHome
if not "%EXIST_HOME%" == "" goto gotExistHome
set EXIST_HOME=..

:gotExistHome
set CATALINA_HOME=%EXIST_HOME%\jakarta-tomcat-4.0.3
set CATALINA_BASE=%CATALINA_HOME%
set _LIBJARS=%EXIST_HOME%;%EXIST_HOME%\exist.jar
for %%i in (%EXIST_HOME%\lib\*.jar) do call bin\cpappend.bat %%i

rem ----- Prepare Appropriate Java Execution Commands -------------------------

if not "%OS%" == "Windows_NT" goto noTitle
set _STARTJAVA=start "Catalina" "%JAVA_HOME%\bin\java"
set _RUNJAVA="%JAVA_HOME%\bin\java"
goto gotTitle
:noTitle
set _STARTJAVA=start "%JAVA_HOME%\bin\java"
set _RUNJAVA="%JAVA_HOME%\bin\java"

:gotTitle
"%CATALINA_HOME%\bin\catalina" stop %1 %2 %3 %4 %5 %6 %7 %8

:finish
