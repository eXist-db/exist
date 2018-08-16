#!/usr/bin/env bash

##
# Common eXist-db script functions and settings for JMX
##

JMX_ENABLED=0
JMX_PORT=1099

check_jmx_status() {
    if [ "${JMX_ENABLED}" -gt 0 ]; then
	JMX_OPTS="-Dcom.sun.management.jmxremote \
		-Dcom.sun.management.jmxremote.port=$JMX_PORT \
		-Dcom.sun.management.jmxremote.authenticate=false \
		-Dcom.sun.management.jmxremote.ssl=false"
	JAVA_OPTIONS="$JAVA_OPTIONS $JMX_OPTS"
    fi
}
