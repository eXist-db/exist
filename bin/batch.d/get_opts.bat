@echo off

set CHECK_PORT=0 

for /f "tokens=*" %%x IN ("%*") DO SET "SUB_CMD_LINE_ARGS=%%x"

:EXTRACT_ARG
    if "%SUB_CMD_LINE_ARGS%" == "" goto :eof

    for /f "tokens=1* delims= " %%x in ("%SUB_CMD_LINE_ARGS%") DO (
       set "SUB_CMD_LINE_ARG=%%x"
       set "SUB_CMD_LINE_ARGS=%%y"    :: shift to the next tag
       goto CHECK_ARG
    )
    if not "%SUB_CMD_LINE_ARGS%" == "" goto EXTRACT_ARG    :: loop to process the next arg
    goto :eof    :: finished processing all args 

:CHECK_ARG

    :: if "%SUB_CMD_LINE_ARG%" == "" goto :EOF

    if %CHECK_PORT% == 1 goto CHECK_IS_PORT

    if not "x%SUB_CMD_LINE_ARG:-j=%" == "x%SUB_CMD_LINE_ARG%" goto JMX_CHECK_PORT

    if not "x%SUB_CMD_LINE_ARG:--jmx=%" == "x%SUB_CMD_LINE_ARG%" goto JMX_CHECK_PORT

    set OPTNAME=--jmx=
    if "%SUB_CMD_LINE_ARG:~0,6%" == %OPTNAME% goto JMX_6_PORT_SET

    set OPTNAME=-j=
    if "%SUB_CMD_LINE_ARG:~0,3%" == %OPTNAME% goto JMX_3_PORT_SET

    set OPTNAME=--jmx
    if "%SUB_CMD_LINE_ARG:~0,5%" == %OPTNAME% goto JMX_5_PORT_SET

    set OPTNAME=-j
    if "%SUB_CMD_LINE_ARG:~0,2%" == %OPTNAME% goto JMX_2_PORT_SET

    set OPTNAME=--j
    if "%SUB_CMD_LINE_ARG:~0,3%" == %OPTNAME% goto JMX_3_PORT_SET

    goto ADD_TO_JAVA_ARGS 


:CHECK_IS_PORT
 echo Check if is port %SUB_CMD_LINE_ARG%
 set CHECK_PORT=0
 if "%SUB_CMD_LINE_ARG:~0,1%" == "-" goto ADD_TO_JAVA_ARGS
 set JMX_PORT=%SUB_CMD_LINE_ARG%
 echo JMX_PORT=%JMX_PORT%
 goto EXTRACT_ARG

:JMX_CHECK_PORT
 echo "JMX enabled"
 set JMX_ENABLED=1
 set CHECK_PORT=1
 echo CHECK_PORT=%CHECK_PORT%
 goto EXTRACT_ARG

:JMX_2_PORT_SET
 set JMX_ENABLED=1
 set JMX_PORT=%SUB_CMD_LINE_ARG:~2%
 echo JMX_PORT=%JMX_PORT%
 goto EXTRACT_ARG

:JMX_3_PORT_SET
 set JMX_ENABLED=1
 set JMX_PORT=%SUB_CMD_LINE_ARG:~3%
 echo JMX_PORT=%JMX_PORT%
 goto EXTRACT_ARG

:JMX_5_PORT_SET
 set JMX_ENABLED=1
 set JMX_PORT=%SUB_CMD_LINE_ARG:~5%
 echo JMX_PORT=%JMX_PORT%
 goto EXTRACT_ARG

:JMX_6_PORT_SET
 set JMX_ENABLED=1
 set JMX_PORT=%SUB_CMD_LINE_ARG:~6%
 echo JMX_PORT=%JMX_PORT%
 goto EXTRACT_ARG

:ADD_TO_JAVA_ARGS
 echo Adding "%SUB_CMD_LINE_ARG%" to JAVA_ARGS...
 for /f "delims=" %%G IN ("%JAVA_ARGS%") DO SET "JAVA_ARGS=%%~G"
 set "JAVA_ARGS=%JAVA_ARGS% %SUB_CMD_LINE_ARG%"
 goto EXTRACT_ARG

