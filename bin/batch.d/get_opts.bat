rem $Id$
@echo off

set /a CHECK_PORT=0 

:CHECK_ARG
    set ARG=%1

    if %ARG%x == x goto :EOF

    if %CHECK_PORT% == 1 goto CHECK_IS_PORT

    if %ARG% == "-j" goto JMX_CHECK_PORT

    if %ARG% == "--j" goto JMX_CHECK_PORT

    if %ARG% == "-jmx" goto JMX_CHECK_PORT

    if %ARG% == "--jmx" goto JMX_CHECK_PORT

    set OPTNAME=-jmx=
    if %ARG:~0,5% == %OPTNAME% goto JMX_PORT_SET

    set OPTNAME=--jmx=
    if %ARG:~0,6% == %OPTNAME% goto JMX_PORT_SET

    set OPTNAME=-j=
    if %ARG:~0,3% == %OPTNAME% goto JMX_PORT_SET

    set OPTNAME=--j=
    if %ARG:~0,4% == %OPTNAME% goto JMX_PORT_SET

    set OPTNAME=-jmx
    if %ARG:~0,4% == %OPTNAME% goto JMX_PORT_SET

    set OPTNAME=--jmx
    if %ARG:~0,5% == %OPTNAME% goto JMX_PORT_SET

    set OPTNAME=-j
    if %ARG:~0,2% == %OPTNAME% goto JMX_PORT_SET

    set OPTNAME=--j
    if %ARG:~0,3% == %OPTNAME% goto JMX_PORT_SET

    goto ADD_TO_JAVA_ARGS 


:CHECK_IS_PORT
 echo Check if is port %ARG%
 set CHECK_PORT=0
 if %ARG:~0,1% == "-" goto ADD_TO_JAVA_ARGS
 set JMX_PORT=%ARG%
 shift
 goto CHECK_ARG

:JMX_CHECK_PORT
 echo "JMX enabled"
 set JMX_ENABLED=1
 set CHECK_PORT=1
 echo CHECK_PORT=%CHECK_PORT%
 shift
 goto CHECK_ARG

:JMX_PORT_SET
 echo Setting port to "%ARG:%OPTNAME%=%"
 set JMX_ENABLED=1
 set JMX_PORT=%ARG:%OPTNAME%=%
 echo JMX_PORT = %JMX_PORT%
 shift
 goto CHECK_ARG

:ADD_TO_JAVA_ARGS
 echo Adding %ARG% to JAVA_ARGS
 set JAVA_ARGS=%JAVA_ARGS% %ARG%
 shift
 goto CHECK_ARG
