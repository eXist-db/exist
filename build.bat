@echo off

rem $Id$

set JAVA_RUN="java"

if not "%JAVA_HOME%" == "" (
    set JAVA_RUN="%JAVA_HOME%\bin\java"
    goto gotJavaHome
)

echo WARNING: JAVA_HOME not found in your environment.
echo.
echo Please, set the JAVA_HOME variable in your enviroment to match the
echo location of the Java Virtual Machine you want to use in case of build run fail.
echo.

:gotJavaHome
if not "%EXIST_HOME%" == "" goto gotExistHome
set EXIST_HOME=%CD%

:gotExistHome
set ANT_HOME=%EXIST_HOME%\tools\ant
set _LIBJARS=%CLASSPATH%;%ANT_HOME%\lib\ant-launcher.jar;%EXIST_HOME%\lib\user\svnkit.jar;%EXIST_HOME%\lib\user\svnkit-cli.jar

set JAVA_ENDORSED_DIRS=%EXIST_HOME%\lib\endorsed

rem You must set
rem -Djavax.xml.transform.TransformerFactory=org.apache.xalan.processor.TransformerFactoryImpl
rem Otherwise Ant will fail to do junitreport with Saxon, as it has a direct dependency on Xalan.

set _JAVA_OPTS=-Xms512m -Xmx512m -Djava.endorsed.dirs="%JAVA_ENDORSED_DIRS%" -Dant.home="%ANT_HOME%" -Dexist.home="%EXIST_HOME%" -Djavax.xml.transform.TransformerFactory="org.apache.xalan.processor.TransformerFactoryImpl" %JAVA_OPTS%

echo eXist Build
echo -------------------
echo JAVA_HOME=%JAVA_HOME%
echo EXIST_HOME=%EXIST_HOME%
echo _LIBJARS=%_LIBJARS%
echo _JAVA_OPTS=%_JAVA_OPTS%

echo Starting Ant...
echo

%JAVA_RUN% %_JAVA_OPTS% -classpath "%_LIBJARS%" org.apache.tools.ant.launch.Launcher %1 %2 %3 %4 %5 %6 %7 %8 %9
