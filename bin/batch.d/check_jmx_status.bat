rem $Id$
@echo off

set JMX_OPTS="-Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=%JMX_PORT% -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false"

if %JMX_ENABLED% == 0 goto :EOF
 set JAVA_OPTS=%JAVA_OPTS% %JMX_OPTS%
