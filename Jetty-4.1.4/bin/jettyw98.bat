@echo off
:: ===========================================================
:: RunJetty.bat
:: ===========================================================
:: This batch file initializes the environment and runs the 
:: Jetty web server under Windows NT. It uses Windows NT cmd
:: extensions and does not work under Window 9x. 
::
::
:: USAGE: 
:: runjetty [configuration files]
:: Example: jetty.bat etc\admin.xml etc\demo.xml
::
:: ENVIRONMENT VARIABLES:
:: The following environment variables should be set to use this
:: batch file. These can be set jettyenv.bat file which willed
:: be called if found in the current working directory.
::
:: JAVA_HOME - this should be set to the directory that the 
:: Java Developers Kit or JDK has been installed.
:: Example: set JAVA_HOME=c:\jdk1.3
::
:: JETTY_HOME - this should be set to the base installation directory
:: where the Jetty server was installed.  The batch file will try to set
:: this on its own by looking for the jetty.jar file in the lib
:: subdirectory.
:: Example: set JETTY_HOME=c:\Jetty-3.1.RC9
::
:: JETTY_PORT
::   Default port for Jetty servers. The default value is 8080. The java 
::   system property "jetty.port" will be set to this value for use in 
::   configure.xml files, f.e:
::
::     <Arg><SystemProperty name="jetty.port" default="80"/></Arg>
::
:: JETTY_OPTIONS - (Optional) Any options to be passed to the JVM 
:: can be set in this variable.  It will have appended to it:
::     -Djetty.home=%JETTY_HOME% 
::
:: NOTES: 
:: -  etc\admin.xml file is always prepended to each set of arguments
::
:: -  The drive and directory are changed during execution of the batch file
::    to make JETTY_HOME the current working directory.  The original drive
::    and directory are restored when Jetty is stopped and the batch file 
::    is completed.
::
:: Created by John T. Bell
:: j_t_bell@yahoo.com
:: September 14th, 2001
:: ===========================================================
rem ===========================================================
rem == save environment variables
rem ===========================================================
setlocal
set x_PATH=%path%
set x_CP=%CP%

rem ===========================================================
rem == save the current directory and drive
rem ===========================================================
echo ..Save current directory and drive

for /F "delims=;" %%i in ('cd') do set x_PWD=%%i
set x_DRIVE=%x_PWD:~0,2%

rem == above gives error in windows98 //jakob

rem ===========================================================
rem == Look for batch file to set environment variables
rem ===========================================================
echo ..Look for jettyenv.bat
IF EXIST jettyenv.bat CALL jettyenv.bat

rem ===========================================================
rem == check for JAVA_HOME environment variable
rem ===========================================================
echo ..Check for java_home
if NOT "%JAVA_HOME%"=="" goto got_java_home
	echo The environment variable JAVA_HOME must be set.
	goto done
:got_java_home

echo ..Check for jetty_home
rem == if JETTY_HOME is not set
if NOT "%JETTY_HOME%"=="" goto got_jetty_home
rem ==   set JETTY_HOME to the current directory

rem ===========================================================
rem == try to set JETTY_HOME by looking for the jetty.jar file
rem ===========================================================
if EXIST .\lib\org.mortbay.jetty.jar goto found_jar
cd ..
if EXIST .\lib\org.mortbay.jetty.jar goto found_jar
        echo The environment variable JETTY_HOME must be set!
        goto done
:found_jar
	for /F "delims=;" %%i in ('cd') do set JETTY_HOME=%%i
:endif1

:got_jetty_home
rem ===========================================================
rem == get Drive information
rem ===========================================================
echo ..Get drive information
if NOT "%JETTY_DRIVE"=="" goto skip 
set JETTY_DRIVE=%JETTY_HOME:~0,2%
:skip

rem ===========================================================
rem == Change directory to the JETTY_HOME root directory.
rem ===========================================================
echo ..Change to jetty_home root directory
%JETTY_DRIVE%
cd "%JETTY_HOME%"


rem ===========================================================
rem == set CLASSPATH
rem ===========================================================
echo ..Set classpath
set CP=%JETTY_HOME%\lib\javax.servlet.jar
set CP=%CP%;%JETTY_HOME%\lib\javax.servlet.jar
set CP=%CP%;%JETTY_HOME%\lib\org.mortbay.jetty.jar
set CP=%CP%;%JETTY_HOME%\lib\org.apache.jasper.jar
set CP=%CP%;%JETTY_HOME%\ext\com.sun.net.ssl.jar
set CP=%CP%;%JETTY_HOME%\ext\javax.xml.jaxp.jar
set CP=%CP%;%JETTY_HOME%\ext\crimson.jar
set CP=%CP%;%JAVA_HOME%\lib\tools.jar
set CP="%CP%"

rem ===========================================================
rem == check for and set command line args
rem ===========================================================
rem == if no args then set admin.xml and demo.xml
rem == note: since we will cd to the JETTY_HOME directory
rem          we do not need to append JETTY_HOME onto the
rem          file names.
echo ..Check and Set command line args

rem for win98 remowe [" 
rem replace %1 with a temp

set temp=%1
if "%temp%"=="run" set ARGS=etc\jetty.xml
if "%temp%"=="demo" set ARGS=etc\admin.xml etc\demo.xml 
if "%temp%"=="" set ARGS=etc\admin.xml etc\demo.xml
	if NOT "%ARGS%"=="" goto args_done

rem == append command line arguments on ARGS
:setargs
if "%temp%=="" goto args_done
	set ARGS=%1 %2 %3 %4 %5 %6 %7 %8 %9 

:args_done

rem ===========================================================
echo ..Check for jetty_port
if NOT "%JETTY_PORT%"=="" goto jetty_port_set
  set JETTY_PORT=8080
:jetty_port_set

rem ===========================================================
rem == build jvm options
rem == doesn´t work in windows 98. Problem the '='
rem ===========================================================
echo ..Build jvm options
set OPTIONS=-Djetty.home="%JETTY_HOME%" -Djetty.port=%JETTY_PORT%

if DEFINED JETTY_OPTIONS set OPTIONS=%OPTIONS% %JETTY_OPTIONS%

rem ===========================================================
rem == build run command
rem == due to problem above runme can't be used.
rem ===========================================================
echo ..Build run command
set RUNME="%JAVA_HOME%\bin\java" -cp %CP% %OPTIONS% org.mortbay.jetty.Server %ARGS% 

rem ===========================================================
rem == echo environment variables to aid in debugging
rem ===========================================================
echo 
echo JAVA_HOME=%JAVA_HOME%
echo JETTY_HOME=%JETTY_HOME%
echo JETTY_DRIVE=%JETTY_DRIVE%
echo JETTY_PORT=%JETTY_PORT%
echo OPTIONS=%OPTIONS%
echo ARGS=%args%
echo RUNME=%RUNME%
echo 

rem ===========================================================
rem == run jetty
rem ===========================================================
rem for win98 replace %options% with the content of %options%
rem gives a problem when starting due to filenotfound when jetty.home set.
rem ..
%JAVA_HOME%\bin\java -Djavax.xml.parsers.DocumentBuilderFactory=org.apache.xerces.jaxp.DocumentBuilderFactoryImpl -Djavax.xml.parsers.SAXParserFactory=org.apache.xerces.jaxp.SAXParserFactoryImpl -Djavax.xml.transform.TransformerFactory=org.apache.xalan.processor.TransformerFactoryImpl -cp %CP% org.mortbay.jetty.Server %ARGS% 
rem %RUNME%
rem java %JAXP% ....
:done
rem ===========================================================
rem == clean up our toys
rem ===========================================================
%x_DRIVE%
cd "%x_PWD%"
set PATH=%x_PATH%
set CP=%x_CP%
set ARGS=
set OPTIONS=
set RUNME=
endlocal
