# -*-Shell-script-*-
# Common eXist script functions and settings for JMX
# $Id:jmx-settings.sh 7231 2008-01-14 22:33:35Z wolfgang_m $

JMX_ENABLED=0
JMX_PORT=1099

check_jmx_status() {
    if [ "${JMX_ENABLED}" -gt 0 ]; then
	JMX_OPTS="-Dcom.sun.management.jmxremote \
		-Dcom.sun.management.jmxremote.port=$JMX_PORT \
		-Dcom.sun.management.jmxremote.authenticate=false \
		-Dcom.sun.management.jmxremote.ssl=false"
	JAVA_OPTIONS="$JAVA_OPTIONS $JMX_OPTS"
	echo "Using JMX: ${JMX_OPTS}"
	
    fi

}
