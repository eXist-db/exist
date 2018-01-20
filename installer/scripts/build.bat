@echo off

rem $Id$

::remove any quotes from JAVA_HOME and EXIST_HOME env var, will be re-added below
for /f "delims=" %%G IN (%JAVA_HOME%) DO SET JAVA_HOME=%%G
for /f "delims=" %%G IN (%EXIST_HOME%) DO SET EXIST_HOME=%%G

if not "%JAVA_HOME%" == "" goto gotJavaHome
set JAVA_HOME=$JAVA_HOME

:gotJavaHome
rem will be set by the installer
set EXIST_HOME=$INSTALL_PATH

:gotExistHome
set ANT_HOME=%EXIST_HOME%\tools\ant
set _LIBJARS=%CLASSPATH%;%ANT_HOME%\lib\ant-launcher-1.10.1.jar;%EXIST_HOME%\lib\test\junit-4.12.jar;%JAVA_HOME%\lib\tools.jar

rem You must set
rem -Djavax.xml.transform.TransformerFactory=org.apache.xalan.processor.TransformerFactoryImpl
rem Otherwise Ant will fail to do junitreport with Saxon, as it has a direct dependency on Xalan.

set _JAVA_OPTS=-Xms512m -Xmx512m -Dant.home="%ANT_HOME%" -Dexist.home="%EXIST_HOME%" -Djavax.xml.transform.TransformerFactory="org.apache.xalan.processor.TransformerFactoryImpl" %JAVA_OPTS%

echo eXist Build
echo -------------------
echo JAVA_HOME=%JAVA_HOME%
echo EXIST_HOME=%EXIST_HOME%
echo _LIBJARS=%_LIBJARS%
echo _JAVA_OPTS=%_JAVA_OPTS%

echo Starting Ant...
echo

"%JAVA_HOME%\bin\java" %_JAVA_OPTS% -classpath "%_LIBJARS%" org.apache.tools.ant.launch.Launcher %1 %2 %3 %4 %5 %6 %7 %8 %9
